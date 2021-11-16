package com.RegressionTestSelectionTool.xmlfields.differences;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.util.ArrayList;
@XStreamAlias("new-classes")
public class NewClassesField {
    @XStreamImplicit
    @XStreamAlias("name")
    public ArrayList<String> names = new ArrayList<>();
}
