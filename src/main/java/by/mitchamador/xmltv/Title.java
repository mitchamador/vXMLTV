package by.mitchamador.xmltv;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class Title {
    //@SerializedName("@lang")
    private String lang;

    //@SerializedName("$")
    private String title;

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Title(String title) {
        this.title = title;
    }

    public Title(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException {
        lang = XMLTV.getAttr(start, "lang");

        while (xmlEventReader.hasNext()) {
            XMLEvent xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isCharacters()) {
                Characters characters = xmlEvent.asCharacters();
                title = characters.getData();
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

    public String toXml(String name, int level) {
        StringBuilder s = new StringBuilder(1024);
        s.append(XMLTV.getIndent(level)).append("<").append(name);
        if (lang != null) {
            s.append(" lang=\"").append(lang).append("\"");
        }
        s.append(">");
        s.append(XMLTV.escapeXml(title, false));
        s.append("</").append(name).append(">\n");
        return s.toString();
    }
}
