package com.example.reactivestudy.main.context;

public class ContextDemo {

    public static void main(String[] args) {
        Context c0 = Context.empty();

        // Operator3 — ближе всего к subscribe()
        Context c1 = c0 == null
                ? new Context("K3", "value3", null)
                : c0.put("K3", "value3");

        System.out.println("Operator3 видит: K3=" + c1.get("K3"));

        // Operator2 — оборачивает c1
        Context c2 = c1.put("K2", "value2");
        System.out.println("Operator2 видит: K3=" + c2.get("K3") + ", K2=" + c2.get("K2"));

        // Operator1 — самый верхний, оборачивает c2
        Context c3 = c2.put("K1", "value1");

        System.out.println("Operator1 видит: K3=" + c3.get("K3")
                + ", K2=" + c3.get("K2") + ", K1=" + c3.get("K1"));
    }
}
