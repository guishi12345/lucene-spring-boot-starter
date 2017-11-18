package com.lucene.starter.service;

import com.lucene.starter.annotation.Analysis;
import com.lucene.starter.common.AnalysisEntity;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LuceneService {

    private Directory directory;

    private String indexPath;

    public void initDirectory() throws Exception {
        directory = FSDirectory.open(Paths.get(indexPath));
    }

    public void analysis(AnalysisEntity entity) throws Exception {
        if (entity == null) {
            return;
        }

        Document doc = new Document();

        List<Field> fieldList = getAnalysisFields(entity.getClass());
        for (Field field : fieldList) {
            field.setAccessible(true);
            doc.add(new StringField(field.getName(), field.get(entity).toString(),
                    org.apache.lucene.document.Field.Store.YES));
        }

        IndexWriter writer = writerBuilder();
        writer.addDocument(doc);
        writer.close();
    }

    public <T> List<T> search(String content, Class<T> clazz) throws Exception {
        IndexReader reader = DirectoryReader.open(directory);
        IndexSearcher is = new IndexSearcher(reader);

        BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();

        List<Field> fieldList = getAnalysisFields(clazz);
        for (Field field : fieldList) {
            Term term = new Term(field.getName(), "*" + content + "*");
            WildcardQuery wildcardQuery = new WildcardQuery(term);
            booleanQuery.add(wildcardQuery, BooleanClause.Occur.SHOULD);
        }

        TopDocs hits = is.search(booleanQuery.build(), 100);
        List<T> entityList = new ArrayList<>();
        for (ScoreDoc scoreDocs : hits.scoreDocs) {
            Document doc = is.doc(scoreDocs.doc);
            Map<String, String> map = new HashMap<>();
            for (Field field : fieldList) {
                map.put(field.getName(), doc.get(field.getName()));
            }
            T entity = clazz.newInstance();
            BeanUtils.copyProperties(entity, map);
            entityList.add(entity);
        }

        return entityList;
    }

    private IndexWriter writerBuilder() throws Exception {
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        return new IndexWriter(directory, iwc);
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

    public String getIndexPath() {
        return indexPath;
    }

    public void setIndexPath(String indexPath) {
        this.indexPath = indexPath;
    }

}
