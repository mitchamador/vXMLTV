package by.mitchamador;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class XMLTV {

    private String filename;
    //@SerializedName("channel")
    private List<Channel> channels = new ArrayList<Channel>();
    //@SerializedName("programme")
    private List<Programme> programmes = new ArrayList<Programme>();

    public void setChannelsOnly(boolean channelsOnly) {
        this.channelsOnly = channelsOnly;
    }

    private boolean channelsOnly;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    public List<Programme> getProgrammes() {
        return programmes;
    }

    public class Programme {
        //@SerializedName("@channel")
        private String channel;
        //@SerializedName("@start")
        private Date start;
        //@SerializedName("@stop")
        private Date stop;

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

        public Date getStart() {
            return start;
        }

        public Date getStop() {
            return stop;
        }

        public Programme(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException, java.text.ParseException {
            this.channel = getAttr(start, "channel");
            if ("1".equals(this.channel)) this.channel = "100500";
            this.start = DATE_FORMAT.parse(getAttr(start, "start"));
            this.stop = DATE_FORMAT.parse(getAttr(start, "stop"));

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
            s.append(getIndent(level)).append("<programme");
            s.append(" channel=\"" + escapeXml(channel) + "\"");
            s.append(" start=\"" + DATE_FORMAT.format(start) + "\"");
            s.append(" stop=\"" + DATE_FORMAT.format(stop) + "\"");
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

            s.append(getIndent(level)).append("</programme>\n");
            return s.toString();
        }
    }

    public class Category {
        //@SerializedName("@lang")
        private String lang;
        //@SerializedName("$")
        private String name;

        public Category(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException {
            lang = getAttr(start, "lang");

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
            StringBuilder s = new StringBuilder(1024);
            s.append(getIndent(level)).append("<").append("category");
            if (lang != null) {
                s.append(" lang=\"").append(lang).append("\"");
            }
            s.append(">");
            s.append(escapeXml(name));
            s.append("</category>\n");
            return s.toString();
        }
    }

    public class Title {
        //@SerializedName("@lang")
        private String lang;

        //@SerializedName("$")
        private String title;

        public Title(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException {
            lang = getAttr(start, "lang");

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
            s.append(getIndent(level)).append("<").append(name);
            if (lang != null) {
                s.append(" lang=\"").append(lang).append("\"");
            }
            s.append(">");
            s.append(escapeXml(title));
            s.append("</").append(name).append(">\n");
            return s.toString();
        }
    }

    public class PreviousShow {
        public PreviousShow() {
            super();
            this.start = null;
        }

        //@SerializedName("@start")
        private String start = null;
    }

    public class EpisodeNumber {
        //@SerializedName("@system")
        private String system;

        //@SerializedName("$")
        private String value;

    }

    public class Channel {
        //@SerializedName("@id")
        private String id;
        //@SerializedName("display-name")
        private List<DisplayName> name = new ArrayList<DisplayName>();
        //@SerializedName("icon")
        private Icon icon;

        public Channel(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException {
            id = getAttr(start, "id");
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
                names[i] = name.get(i).name;
            }
            return names;
        }

        public String toXml(int level) {
            StringBuilder s = new StringBuilder(1024);
            s.append(getIndent(level)).append("<channel");
            s.append(" id=\"").append(id).append("\"");
            s.append(">\n");

            for (DisplayName dn : name) {
                s.append(dn.toXml(level + 1));
            }

            if (icon != null) {
                s.append(icon.toXml(level + 1));
            }

            s.append(getIndent(level)).append("</channel>\n");
            return s.toString();
        }

    }

    public class DisplayName {
        //@SerializedName("@lang")
        private String lang;

        //@SerializedName("$")
        private String name;

        public DisplayName(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException {
            lang = getAttr(start, "lang");

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
            s.append(getIndent(level)).append("<display-name");
            if (lang != null) {
                s.append(" lang=\"").append(lang).append("\"");
            }
            s.append(">");
            s.append(name);
            s.append("</display-name>\n");
            return s.toString();
        }
    }

    public class Icon {
        //@SerializedName("@src")
        private String src;

        //@SerializedName("@width")
        private int width;

        //@SerializedName("@height")
        private int height;

        public Icon(XMLEventReader xmlEventReader, StartElement start) throws XMLStreamException {
            src = getAttr(start, "src");
            width = getAttrIntValue(start, "width");
            height = getAttrIntValue(start, "height");

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
            s.append(getIndent(level)).append("<icon");
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

    public static final String DATE_FORMAT_STRING = "yyyyMMddHHmmss Z";
    public SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STRING);

    public String getAttr(StartElement start, String attr) {
        for (Iterator<Attribute> it = start.getAttributes(); it.hasNext(); ) {
            Attribute a = it.next();
            if (a.getName().getLocalPart().equals(attr)) {
                return a.getValue();
            }
        }
        return null;
    }

    public int getAttrIntValue(StartElement start, String attr) {
        for (Iterator<Attribute> it = start.getAttributes(); it.hasNext(); ) {
            Attribute a = it.next();
            if (a.getName().getLocalPart().equals(attr)) {
                try {
                    return Integer.parseInt(a.getValue());
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        return 0;
    }

    static String escapeXml(String st){
        if (st == null) return "";
        //st = st.replaceAll("[^\\u0020-\\uD7FF\\uE000-\\uFFFD\\t\\n\\r]", " ");
        st = st.replaceAll("&", "&amp;");
        //st = st.replaceAll("'", "&apos;");
        //st = st.replaceAll(">", "&gt;");
        //st = st.replaceAll("<", "&lt;");
        //st = st.replaceAll("\"", "&quot;");
        return st;
    }

    static String getIndent(int level) {
        StringBuilder s = new StringBuilder(128);
        for (int i = 0; i < level; i++) {
            s.append('\t');
        }
        return s.toString();
    }

    public XMLTV() {
    }

    public void parseStax(String filename) throws IOException {

        this.filename = filename;

        InputStream in;
        URLConnection con = null;
        if (filename.startsWith("http://") || filename.startsWith("https://")) {
            URL url = new URL(filename);
            con = url.openConnection();
            in = new BufferedInputStream(con.getInputStream());
        } else {
            in = new BufferedInputStream(new FileInputStream(filename));
        }

        if (filename.endsWith(".gz")) {
            in = new GZIPInputStream(con == null ? in : con.getInputStream());
        }

        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, (c.get(Calendar.HOUR_OF_DAY) / 6) * 6);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        long begin = c.getTime().getTime();
        c.add(Calendar.HOUR_OF_DAY, 3 * 24);
        long end = c.getTime().getTime();

        try {
            XMLEventReader xmlEventReader = xmlInputFactory.createXMLEventReader(in);

            while (xmlEventReader.hasNext()) {
                XMLEvent xmlEvent = xmlEventReader.nextEvent();
                if (xmlEvent.isStartElement()) {
                    StartElement start = xmlEvent.asStartElement();
                    String name = start.getName().getLocalPart();
                    if (name.equals("channel")) {
                        channels.add(new Channel(xmlEventReader, start));
                    } else if (!channelsOnly && name.equals("programme")) {
                        try {
                            Programme p = new Programme(xmlEventReader, start);
                            if (p.start != null && p.stop != null
                                    && ((p.start.getTime() >= begin && p.start.getTime() <= end) || (p.stop.getTime() >= begin && p.stop.getTime() <= end))
                                    ) {
                                programmes.add(p);
                            }
                        } catch (java.text.ParseException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }

}