package by.mitchamador.xmltv;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static by.mitchamador.xmltv.XMLTV.DATE_FORMATTER;

public class Programme {
    //@SerializedName("@channel")
    private String channel;
    //@SerializedName("@start")
    private ZonedDateTime start;
    //@SerializedName("@stop")
    private ZonedDateTime stop;

    //@SerializedName("title")
    private Title title;

    //@SerializedName("sub-title")
    private Title subtitle = null;

    //@SerializedName("desc")
    private Title description = null;

//  //@SerializedName("previously-shown")
//  public PreviousShow previousShow = new PreviousShow();

    //@SerializedName("episode-num")
    private EpisodeNumber episodeNumber;

    //@SerializedName("category")
    private List<Category> categories = new ArrayList<Category>();

    public String getChannel() {
        return channel;
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public ZonedDateTime getStop() {
        return stop;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setStart(ZonedDateTime start) {
        this.start = start;
    }

    public void setStop(ZonedDateTime stop) {
        this.stop = stop;
    }

    public Title getTitle() {
        return title;
    }

    public void setTitle(Title title) {
        this.title = title;
    }

    public Title getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(Title subtitle) {
        this.subtitle = subtitle;
    }

    public Title getDescription() {
        return description;
    }

    public void setDescription(Title description) {
        this.description = description;
    }

    public EpisodeNumber getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(EpisodeNumber episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    public Programme(String channel) {
        this.channel = channel;
    }

    public Programme(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException, java.text.ParseException {
        this.channel = XMLTV.getAttr(start, "channel");
        if ("1".equals(this.channel)) {
            this.channel = "100500";
        }
        String startAttr = XMLTV.getAttr(start, "start");
        this.start = startAttr == null ? ZonedDateTime.now() : ZonedDateTime.parse(startAttr, DATE_FORMATTER);
        String stopAttr = XMLTV.getAttr(start, "stop");
        this.stop = stopAttr == null ? ZonedDateTime.now() : ZonedDateTime.parse(stopAttr, DATE_FORMATTER);

        while (xmlEventReader.hasNext()) {
            XMLEvent xmlEvent = xmlEventReader.nextEvent();
            if (xmlEvent.isStartElement()) {
                StartElement startElement = xmlEvent.asStartElement();
                String name = startElement.getName().getLocalPart();
                if (name.equals("title")) {
                    title = new Title(xmlEventReader, startElement);
                } else if (name.equals("subtitle")) {
                    subtitle = new Title(xmlEventReader, startElement);
                } else if (name.equals("desc")) {
                    description = new Title(xmlEventReader, startElement);
                } else if (name.equals("category")) {
                    categories.add(new Category(xmlEventReader, startElement));
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

    public String toXml(int level) {
        StringBuilder s = new StringBuilder(1024);
        s.append(XMLTV.getIndent(level)).append("<programme");
        s.append(" channel=\"").append(XMLTV.escapeXml(channel, true)).append("\"");
        s.append(" start=\"").append(DATE_FORMATTER.format(start)).append("\"");
        s.append(" stop=\"").append(DATE_FORMATTER.format(stop)).append("\"");
        s.append(">\n");

        if (title != null) {
            s.append(title.toXml("title", level + 1));
        }

        if (subtitle != null) {
            s.append(subtitle.toXml("sub-title", level + 1));
        }

        if (description != null) {
            s.append(description.toXml("desc", level + 1));
        }

        for (Category c : categories) {
            s.append(c.toXml(level + 1));
        }

        s.append(XMLTV.getIndent(level)).append("</programme>\n");
        return s.toString();
    }
}
