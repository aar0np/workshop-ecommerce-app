package com.datastax.tutorials;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.datastax.oss.driver.api.core.CqlSession;

@SpringBootTest
public class Test01_Connectivity {

    @Autowired
    private CqlSession cqlSession;
    
    @Test
    void should_display_cluster() {
    	System.out.print("Connected to cluster: ");
    	cqlSession.execute("SELECT cluster_name FROM system.local WHERE key='local'")
    			.all().stream()
    			.map(r -> r.getString("cluster_name"))
    			.forEach(System.out::println);
    }
    
    @Test
    void should_display_cqlSession() {
        System.out.println("Execute some Cql (CqlSession)");
        System.out.println("+ Your Keyspace: " + cqlSession.getKeyspace().get());
        System.out.println("+ Product Categories: ");
        cqlSession.execute("SELECT name FROM category WHERE parent_id = ffdac25a-0244-4894-bb31-a0884bc82aa9")
                .all().stream()
                .map(r -> r.getString("name"))
                .forEach(System.out::println);
    }
    
}
