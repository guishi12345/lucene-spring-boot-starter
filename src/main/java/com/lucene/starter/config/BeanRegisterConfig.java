package com.lucene.starter.config;

import com.lucene.starter.service.LuceneService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
@ConditionalOnClass(BeanRegisterConfig.class)
@EnableConfigurationProperties(LuceneProperties.class)
public class BeanRegisterConfig {

    @Resource
    private LuceneProperties luceneProperties;

    @Bean
    @ConditionalOnProperty(prefix = "lucene.starter", value = "indexPath", matchIfMissing = false)
    public LuceneService luceneService() throws Exception {
        LuceneService luceneService = new LuceneService();
        luceneService.setIndexPath(luceneProperties.getIndexPath());
        return luceneService;
    }

}
