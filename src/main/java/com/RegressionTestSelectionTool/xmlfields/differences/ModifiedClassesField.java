package com.RegressionTestSelectionTool.xmlfields.differences;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.util.ArrayList;

@XStreamAlias("modified-classes")
public class ModifiedClassesField {
    @XStreamImplicit
    @XStreamAlias("class")
    public ArrayList<ModifiedClassField> classes = new ArrayList<>();
}
