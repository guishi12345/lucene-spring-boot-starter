package com.lucene.starter.service;

import com.lucene.starter.annotation.Analysis;
import com.lucene.starter.annotation.Unique;
import com.lucene.starter.common.AnalysisEntity;
import com.lucene.starter.common.Pagination;
import com.lucene.starter.common.exception.NoUniqueDocException;
import com.lucene.starter.common.exception.NoUniqueFieldException;
import com.lucene.starter.common.exception.NoValueException;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LuceneService {

    /**
     * Directory仓储
     */
    private Map<String, Directory> directoryRepertory = new HashMap<>();

    /**
     * 所有索引目录的根目录
     */
    private String indexPath;

    public void analysis(AnalysisEntity entity) throws Exception {
        IndexWriter writer = getWriter(entity.getClass().getSimpleName());
        doAdd(entity, writer);
        commitAndCloseWriter(writer);
    }

    public void abandon(AnalysisEntity entity) throws Exception {
        IndexWriter writer = getWriter(entity.getClass().getSimpleName());
        long line = doDelete(entity, writer);
        if (Long.compare(line, 1L) > 0) {
            rollbackAndCloseWriter(writer);
            throw new NoUniqueDocException("There is not only one document when executed abandon !");
        }
        commitAndCloseWriter(writer);
    }

    public void update(AnalysisEntity entity) throws Exception {
        IndexWriter writer = getWriter(entity.getClass().getSimpleName());
        long line = doUpdate(entity, writer);
        if (Long.compare(line, 1L) > 0) {
            rollbackAndCloseWriter(writer);
            throw new NoUniqueDocException("There is not only one document when executed update !");
        }
        commitAndCloseWriter(writer);
    }

    public <T> Pagination<T> paginationSearch(String content, Class<T> clazz, int currentPage, int pageSize) throws Exception {
        Pagination<T> page = new Pagination<>(currentPage, pageSize);

        IndexReader reader = getReader(clazz.getSimpleName());
        IndexSearcher searcher = new IndexSearcher(reader);

        BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
        List<Field> fieldList = getAnalysisFields(clazz);
        for (Field field : fieldList) {
            Term term = new Term(field.getName(), "*" + (StringUtils.isEmpty(content) ? "" : (content + "*")));
            WildcardQuery wildcardQuery = new WildcardQuery(term);
            booleanQuery.add(wildcardQuery, BooleanClause.Occur.SHOULD);
        }
        Query query = booleanQuery.build();
        //符合查询条件的总记录数
        int tolSum = searcher.count(query);
        int totalPage = (tolSum % pageSize == 0) ? tolSum/pageSize : (tolSum/pageSize + 1);
        page.setTotalPage(totalPage);
        page.setTotalSum(tolSum);

        ScoreDoc before = null;
        if(page.getCurrentPage() != 1){
            TopDocs docsBefore = searcher.search(query, (page.getCurrentPage() - 1) * page.getPageSize());
            ScoreDoc[] scoreDocs = docsBefore.scoreDocs;
            if(scoreDocs.length > 0){
                before = scoreDocs[scoreDocs.length - 1];
            }
        }

        TopDocs hits = searcher.searchAfter(before, query, page.getPageSize());
        // 搜索返回的结果集合
        ScoreDoc[] scoreDocs = hits.scoreDocs;

        List<T> entityList = new ArrayList<>();
        for (ScoreDoc scoreDoc : scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            Map<String, String> map = new HashMap<>();
            for (Field field : fieldList) {
                map.put(field.getName(), doc.get(field.getName()));
            }
            T entity = clazz.newInstance();
            BeanUtils.copyProperties(entity, map);
            entityList.add(entity);
        }
        page.setData(entityList);
        reader.close();
        return page;
    }

    private long doDelete (AnalysisEntity entity, IndexWriter writer) throws Exception {
        Term accurateTerm = createAccurateTerm(entity);
        return writer.deleteDocuments(accurateTerm);
    }

    private long doUpdate (AnalysisEntity entity, IndexWriter writer) throws Exception {
        Term accurateTerm = createAccurateTerm(entity);
        return writer.updateDocument(accurateTerm, createDoc(entity));
    }

    private long doAdd(AnalysisEntity entity, IndexWriter writer) throws Exception {
        if (entity == null) {
            return -1;
        }
        return writer.addDocument(createDoc(entity));
    }

    private Document createDoc(AnalysisEntity entity) throws Exception {
        Document doc = new Document();
        List<Field> fieldList = getAnalysisFields(entity.getClass());
        for (Field field : fieldList) {
            field.setAccessible(true);
            Object data = field.get(entity);
            if (data != null) {
                doc.add(new StringField(field.getName(), data.toString(),
                        org.apache.lucene.document.Field.Store.YES));
            }
            field.setAccessible(false);
        }
        return doc;
    }

    private void commitAndCloseWriter(IndexWriter writer) throws Exception {
        if (writer != null && writer.isOpen()) {
            writer.commit();
            writer.close();
        }
    }

    private void rollbackAndCloseWriter(IndexWriter writer) throws Exception {
        if (writer != null && writer.isOpen()) {
            writer.rollback();
            writer.close();
        }
    }


    private IndexWriter getWriter(String directoryName) throws Exception {
        Directory directory = getDirectory(directoryName);
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        return new IndexWriter(directory, config);
    }

    private IndexReader getReader(String directoryName) throws Exception {
        Directory directory = getDirectory(directoryName);
        IndexReader reader = DirectoryReader.open(directory);
        return reader;
    }

    private Directory getDirectory(String directoryName) throws Exception {
        Directory directory = directoryRepertory.get(directoryName);
        if (directory == null) {
            String path = indexPath + "/" + directoryName;
            File file = new File(path);
            if (!file.exists()) {
                file.createNewFile();
            }
            directory = FSDirectory.open(Paths.get(path));
            //在索引库没有建立并且没有索引文件的时候首先要commit一下让他建立一个索引库的版本信息
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            IndexWriter writer = new IndexWriter(directory, config);
            commitAndCloseWriter(writer);

            directoryRepertory.put(directoryName, directory);
        }
        return directory;
    }

    private List<Field> getAnalysisFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        List<Field> fieldList = new ArrayList<>();
        for (Field field : fields) {
            if (field.getAnnotation(Analysis.class) != null) {
                fieldList.add(field);
            }
        }
        return fieldList;
    }

    private Field getUniqueFile(List<Field> analysisFields) throws Exception {
        List<Field> uniqueFile = new ArrayList<>();
        for (Field field : analysisFields) {
            if (field.getAnnotation(Unique.class) != null){
                uniqueFile.add(field);
            }
        }
        if (uniqueFile.size() != 1) {
            throw new NoUniqueFieldException("There is not only one unique field !");
        }
        return uniqueFile.get(0);
    }

    private Term createAccurateTerm(AnalysisEntity entity) throws Exception {
        List<Field> analysisFields = getAnalysisFields(entity.getClass());
        Field uniqueField = getUniqueFile(analysisFields);
        uniqueField.setAccessible(true);
        Object value = uniqueField.get(entity);
        uniqueField.setAccessible(false);
        if (value == null) {
            throw new NoValueException("The unique field don't have a value !");
        }
        Term accurateTerm = new Term(uniqueField.getName(), value.toString());
        return accurateTerm;
    }

    public String getIndexPath() {
        return indexPath;
    }

    public void setIndexPath(String indexPath) {
        this.indexPath = indexPath;
    }

}
