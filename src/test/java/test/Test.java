package test;

import com.lucene.starter.service.LuceneService;

public class Test {

    @org.junit.Test
    public void test_1() {

        System.out.println(String.class.getClassLoader());

        System.out.println(int.class.getClassLoader());

        System.out.println(Enum.class.getClassLoader());

        System.out.println(LuceneService.class.getClassLoader());

    }

}
