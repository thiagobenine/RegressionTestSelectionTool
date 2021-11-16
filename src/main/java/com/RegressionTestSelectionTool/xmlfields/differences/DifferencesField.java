package com.RegressionTestSelectionTool.xmlfields.differences;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("differences")
public class DifferencesField {
    public String name;
    @XStreamAlias("modified-classes")
    public ModifiedClassesField modifiedClassesField;

    @XStreamAlias("new-classes")
    public NewClassesField newClassesField;

    @XStreamAlias("new-packages")
    public NewPackagesField newPackagesField;
}
