package by.mitchamador.xmltv;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class Icon {
    //@SerializedName("@src")
    private String src;

    //@SerializedName("@width")
    private int width;

    //@SerializedName("@height")
    private int height;

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public Icon(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException {
        src = XMLTV.getAttr(start, "src");
        width = XMLTV.getAttrIntValue(start, "width");
        height = XMLTV.getAttrIntValue(start, "height");

        while (xmlEventReader.hasNext()) {
            XMLEvent xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isEndElement()) {
                EndElement endElement = xmlEvent.asEndElement();
                String name = endElement.getName().getLocalPart();
                if (name.equals(start.getName().getLocalPart())) {
                    break;
                }
            }
        }
    }

    public String toXml(int level) {
        StringBuilder s = new StringBuilder(256);
        s.append(XMLTV.getIndent(level)).append("<icon");
        if (width != 0) {
            s.append(" width=\"").append(width).append("\"");
        }
        if (height != 0) {
            s.append(" height=\"").append(height).append("\"");
        }
        if (src != null) {
            s.append(" src=\"").append(src).append("\"");
        }
        s.append("/>\n");
        return s.toString();
    }
}
