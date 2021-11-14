package com.RegressionTestSelectionTool.xmlfields.dependencies;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import java.util.ArrayList;

@XStreamAlias("dependencies")
public class DependenciesField {
    @XStreamImplicit
    @XStreamAlias("package")
    public ArrayList<PackageField> packages = new ArrayList<>();
}
