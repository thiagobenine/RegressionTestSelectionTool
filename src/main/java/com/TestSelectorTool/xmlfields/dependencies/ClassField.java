package com.RegressionTestSelectionTool.xmlfields.dependencies;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import java.util.ArrayList;

@XStreamAlias("class")
public class ClassField {
    @XStreamAsAttribute
    public String confirmed;

    public String name;

    @XStreamImplicit
    @XStreamAlias("inbound")
    public ArrayList<InboundField> inbounds = new ArrayList<>();

    @XStreamImplicit
    @XStreamAlias("outbound")
    public ArrayList<OutboundField> outbounds = new ArrayList<>();
}
