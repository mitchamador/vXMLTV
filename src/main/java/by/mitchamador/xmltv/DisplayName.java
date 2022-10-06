package by.mitchamador.xmltv;

import org.json.XML;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class DisplayName {
    //@SerializedName("@lang")
    private String lang;

    //@SerializedName("$")
    private String name;

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DisplayName() {
    }

    public DisplayName(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException {
        lang = XMLTV.getAttr(start, "lang");

        while (xmlEventReader.hasNext()) {
            XMLEvent xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isCharacters()) {
                Characters characters = xmlEvent.asCharacters();
                name = characters.getData();
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

    public String toXml(int level) {
        StringBuilder s = new StringBuilder(256);
        s.append(XMLTV.getIndent(level)).append("<display-name");
        if (lang != null) {
            s.append(" lang=\"").append(lang).append("\"");
        }
        s.append(">");
        s.append(XMLTV.escapeXml(name, false));
        s.append("</display-name>\n");
        return s.toString();
    }
}
