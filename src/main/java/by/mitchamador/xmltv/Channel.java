package by.mitchamador.xmltv;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.List;

public class Channel {
    //@SerializedName("@id")
    private String id;
    //@SerializedName("display-name")
    private List<DisplayName> name = new ArrayList<DisplayName>();
    //@SerializedName("icon")
    private Icon icon;

    public Channel(String id) {
        this.id = id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(List<DisplayName> name) {
        this.name = name;
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
    }

    public Channel(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException {
        id = XMLTV.getAttr(start, "id");
        if ("1".equals(id)) id = "100500";

        while (xmlEventReader.hasNext()) {
            XMLEvent xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isStartElement()) {
                StartElement startElement = xmlEvent.asStartElement();
                String name = startElement.getName().getLocalPart();
                if (name.equals("display-name")) {
                    this.name.add(new DisplayName(xmlEventReader, startElement));
                } else if (name.equals("icon")) {
                    icon = new Icon(xmlEventReader, startElement);
                }
            }
            if (xmlEvent.isEndElement()) {
                EndElement endElement = xmlEvent.asEndElement();
                String name = endElement.getName().getLocalPart();
                if (name.equals(start.getName().getLocalPart())) {
                    break;
                }
            }
        }
    }

    public String getId() {
        return id != null ? id.trim() : "";
    }

    public String getName() {
        String names = "";
        for (String name : getNameList()) {
            names += (names.isEmpty() ? "" : ", ") + name;
        }
        return names;
    }

    public String[] getNameList() {
        String[] names = new String[name.size()];
        for (int i = 0; i < name.size(); i++) {
            names[i] = name.get(i).getName();
        }
        return names;
    }

    public String toXml(int level) {
        StringBuilder s = new StringBuilder(1024);
        s.append(XMLTV.getIndent(level)).append("<channel");
        s.append(" id=\"").append(id).append("\"");
        s.append(">\n");

        for (DisplayName dn : name) {
            s.append(dn.toXml(level + 1));
        }

        if (icon != null) {
            s.append(icon.toXml(level + 1));
        }

        s.append(XMLTV.getIndent(level)).append("</channel>\n");
        return s.toString();
    }

}
