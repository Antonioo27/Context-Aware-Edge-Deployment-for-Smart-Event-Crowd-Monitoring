package it.unibo.cas.eventmanagement;

import org.n52.jackson.datatype.jts.JtsModule;

public class TestJts {
    public static void main(String[] args) {
        JtsModule module = new JtsModule();
        System.out.println("Module created successfully!");
    }
}
