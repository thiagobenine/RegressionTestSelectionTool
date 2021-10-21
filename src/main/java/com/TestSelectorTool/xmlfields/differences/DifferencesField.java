package com.TestSelectorTool.xmlfields.differences;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("differences")
public class DifferencesField {
    public String name;
    @XStreamAlias("modified-classes")
    public ModifiedClassesField modifiedClassesField;

    @XStreamAlias("new-classes")
    public NewClassesField newClassesField;
}
