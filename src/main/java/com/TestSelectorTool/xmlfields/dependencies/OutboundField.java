package com.RegressionTestSelectionTool.xmlfields.dependencies;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;

@XStreamAlias("outbound")
@XStreamConverter(value= ToAttributedValueConverter.class, strings={"text"})
public class OutboundField {
    @XStreamAsAttribute
    public String type;

    @XStreamAsAttribute
    public String confirmed;

    public String text;
}
