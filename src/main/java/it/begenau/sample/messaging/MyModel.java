package it.begenau.sample.messaging;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class MyModel {
    private String val;

    public MyModel() {

    }

    public MyModel(String val) {
        this.val = val;
    }

    public String getVal() {
        return val;
    }

    public void setVal(String val) {
        this.val = val;
    }
}
