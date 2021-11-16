package com.RegressionTestSelectionTool.xmlfields.dependencies;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import java.util.ArrayList;

@XStreamAlias("package")
public class PackageField {
    @XStreamAsAttribute
    public String confirmed;

    public String name;

    @XStreamImplicit
    @XStreamAlias("class")
    public ArrayList<ClassField> classes = new ArrayList<>();
}
