package by.mitchamador.vxmltv;

import by.mitchamador.xmltv.Channel;
import by.mitchamador.xmltv.Programme;
import by.mitchamador.xmltv.XMLTV;
import com.iptv.parser.M3UFile;
import com.iptv.parser.M3UItem;
import com.iptv.parser.M3UToolSet;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class vXMLTV {

    private String[] filelistM3U;
    private String[] filelistXmlTv;

    private String out;
    private boolean quiet;
    private boolean debug;
    private int threads = 1;
    private boolean channelsOnly;

    public vXMLTV(String[] args) {
        parseArgs(args);
    }

    private void run() {
        long start = System.currentTimeMillis();

        List<M3UFile> listM3U = loadM3U();

        XMLTV epg = loadXmlTv();

        Map<String, Channel> channelsXML = matchChannels(epg, listM3U);

        createXmlTv(epg, channelsXML);

        if (debug) {
            System.out.println("total time: " + (double) (System.currentTimeMillis() - start) / (double) 1000 + "\n");
            System.out.println("done.");
        }

    }

    private void createXmlTv(XMLTV epg, Map<String, Channel> channelsXML) {
        if (out != null) {
            StringBuilder xml = new StringBuilder(512 * 1024);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<tv>\n");

            for (Channel ch : channelsXML.values()) {
                xml.append(ch.toXml(1));
            }

/*
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, (c.get(Calendar.HOUR_OF_DAY) / 6) * 6);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            long begin = c.getTime().getTime();
            c.add(Calendar.HOUR_OF_DAY, 3 * 24);
            long end = c.getTime().getTime();

            for (Programme p : epg.programmes) {
                Channel ch = channelsXML.get(p.channel);
                if (ch != null && p.start != null && p.stop != null
                        && ((p.start.getTime() >= begin && p.start.getTime() <= end) || (p.stop.getTime() >= begin && p.stop.getTime() <= end))
                        ) {
                    xml.append(p.toXml(1));
                }
            }
*/
            for (Programme p : epg.getProgrammes()) {
                Channel ch = channelsXML.get(p.getChannel());
                if (ch != null && p.getStart() != null && p.getStop() != null) {
                    xml.append(p.toXml(1));
                }
            }

            xml.append("</tv>\n");

            try {
                saveFile(out, xml.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Map<String, Channel> matchChannels(XMLTV epg, List<M3UFile> listM3U) {

        Set<String> aliases = new HashSet<>();
        Set<String> missed = new HashSet<>();
        Map<String, Channel> channelsXML = new HashMap<>();

        int countM3U = 0;

        for (M3UFile m3u : listM3U) {
            for (M3UItem item : m3u.getItems()) {

                Channel matchedChannel = matchChannel(item, epg.getChannels(), aliases);

                if (matchedChannel != null) {
                    channelsXML.put(matchedChannel.getId(), matchedChannel);
                } else {
                    missed.add(item.getNameM3U());
                }

                countM3U++;
            }
        }

        if (!quiet && !aliases.isEmpty()) {
            System.out.println("=== aliases ===\n" + getPrintedList("\n", () -> new ArrayList<>(aliases)) + "\n");
        }

        if (!quiet && !missed.isEmpty()) {
            System.out.println("=== missed channels ===\n" + getPrintedList("\n", () -> new ArrayList<>(missed)) + "\n");
        }

        if (!quiet) {
            System.out.println("total channels: m3u - " + countM3U + ", aliases - " + aliases.size() +
                    ", missed - " + missed.size() + ", xmltv - " + channelsXML.size() + "\n");
        }

        return channelsXML;
    }

    private Channel matchChannel(M3UItem item, List<Channel> channels, Set<String> aliases) {

        String nameM3U = item.getNameM3U();

        // match by tvg-id and channel id
        for (Channel ch : channels) {
            if (ch.getId().equalsIgnoreCase(item.getTvgId())) {
                return ch;
            }
        }

        // match by tvg-name and channel id
        for (Channel ch : channels) {
            if (ch.getId().equalsIgnoreCase(item.getTvgName())) {
                return ch;
            }
        }

        // match by m3u name and display-name
        for (Channel ch : channels) {
            for (String nameXML : ch.getNameList()) {
                if (nameXML.equalsIgnoreCase(nameM3U)) {
                    return ch;
                }
            }
        }

        // match by converted m3u name and converted display-name
        for (Channel ch : channels) {
            for (String nameXML : ch.getNameList()) {
                if (getConvertedChannelName(nameXML).equalsIgnoreCase(getConvertedChannelName(nameM3U))) {
                    if (item.getTvgId() != null && !item.getTvgId().isEmpty()) {
                        aliases.add(nameM3U + ":" + nameXML);
                    } else {
                        aliases.add(nameM3U + ":id=\"" + ch.getId() + "\"");
                    }
                    return ch;
                }
            }
        }

        return null;
    }

    private XMLTV loadXmlTv() {
        XMLTV epg = new XMLTV();
        epg.setChannelsOnly(channelsOnly);

        ExecutorService pool = Executors.newFixedThreadPool(threads <= 0 ? Runtime.getRuntime().availableProcessors() : threads);

        ExecutorCompletionService<XMLTV> executorCompletionService = new ExecutorCompletionService<>(pool);

        for (final String file : filelistXmlTv) {
           executorCompletionService.submit(() -> {
                XMLTV epg1 = new XMLTV();
                epg1.setChannelsOnly(channelsOnly);

                try {
                    long _start = System.currentTimeMillis();

                    epg1.setFilename(file);
                    epg1.parseStax(file);

                    if (debug) {
                        System.out.println(file + " " + (double) (System.currentTimeMillis() - _start) / (double) 1000 + " sec, total channels: " + epg1.getChannels().size() + ", total programmes: " + epg1.getProgrammes().size() + "\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return epg1;
            });
        }

        pool.shutdown();

        ArrayList<XMLTV> list = new ArrayList<>();

        try {
            Future<XMLTV> f;
            while ((f = pool.isTerminated() ? executorCompletionService.poll() : executorCompletionService.take()) != null) {
                try {
                    XMLTV x = f.get();

                    System.out.println("channel's list from xmltv: " + x.getFilename() + "\n" + getPrintedList(", ", () -> {
                        ArrayList<String> sList = new ArrayList<>();
                        for (Channel c : x.getChannels()) {
                            sList.add(c.getName());
                        }
                        Collections.sort(sList);
                        return sList;
                    }) + "\n");

                    list.add(x);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (XMLTV x : list) {
            epg.getChannels().addAll(x.getChannels());
            epg.getProgrammes().addAll(x.getProgrammes());
            if (!debug && !quiet && channelsOnly) {
                System.out.println("=== " + x.getFilename() + "\n" + getPrintedList("\n", () -> {
                    List<String> sList = new ArrayList<>();
                    for (Channel ch : x.getChannels()) {
                        sList.add(ch.getName());
                    }
                    return sList;
                }) + "\n");
            }
        }

        epg.getChannels().sort(Comparator.comparing(Channel::getName));

        return epg;
    }

    private List<M3UFile> loadM3U() {
        List<M3UFile> listM3U = new ArrayList<>();
        for (String file : filelistM3U) {
            M3UFile m3u = M3UToolSet.load(file);
            listM3U.add(m3u);

            if (debug) {
                System.out.println("channel's list from m3u: " + file + "\n" + getPrintedList(", ", () -> {
                    ArrayList<String> sList = new ArrayList<>();
                    for (M3UItem item : m3u.getItems()) {
                        sList.add((item.getTvgName() != null ? (item.getTvgName().replaceAll("_", " ") + " - ") : "") + item.getChannelName());
                    }
                    //Collections.sort(sList);
                    return sList;
                }) + "\n");
            }
        }
        return listM3U;
    }

    private String getPrintedList(String delimeter, Supplier<List<String>> listSupplier) {
        StringBuilder sb = new StringBuilder();
        for (String s : listSupplier.get()) {
            if (sb.length() != 0) sb.append(delimeter);
            sb.append(s);
        }
        return sb.toString();
    }

    private void parseArgs(String[] args) {
        ArrayList<String> m3uList = new ArrayList<>();
        ArrayList<String> xmlTvList = new ArrayList<>();
        if (args != null) {
            int c = 0;
            while (c < args.length) {
                String arg = args[c].toLowerCase();
                switch (arg) {
                    case "-u":
                        c++;
                        if (c < args.length) {
                            m3uList.add(args[c]);
                        }
                        break;
                    case "-x":
                        c++;
                        if (c < args.length) {
                            xmlTvList.add(args[c]);
                        }
                        break;
                    case "-j":
                        c++;
                        if (c < args.length) {
                            try {
                                threads = Integer.parseInt(args[c]);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        break;
                    case "-o":
                        c++;
                        if (c < args.length) {
                            out = args[c];
                        }
                        break;
                    case "-q":
                        quiet = true;
                        break;
                    case "-c":
                        channelsOnly = true;
                        break;
                    case "-d":
                        debug = true;
                        break;
                }
                c++;
            }
        }
        filelistM3U = m3uList.toArray(new String[0]);
        filelistXmlTv = xmlTvList.toArray(new String[0]);
    }


    private String getConvertedChannelName(String s) {
        try {
            return s.toLowerCase().replaceAll("[ _\\-()]|[hd]", "");
        } catch (Exception e) {
            e.printStackTrace();
            return s;
        }
    }

    public static void main(String[] args) {

        vXMLTV v = new vXMLTV(args);
        v.run();

    }

    private static void saveFile(String filePath, byte[] buffer) throws IOException {
        try (FileOutputStream fo = new FileOutputStream(filePath)) {
            GZIPOutputStream gzip = filePath.endsWith(".gz") ? new GZIPOutputStream(fo) : null;
            OutputStream out = gzip == null ? fo : gzip;

            ByteArrayInputStream bi = new ByteArrayInputStream(buffer);
            byte[] buf = new byte[262144];

            int x;
            while ((x = bi.read(buf)) != -1) {
                out.write(buf, 0, x);
            }

            if (gzip != null) {
                gzip.finish();
            }

            out.flush();
        } catch (IOException ignored) {
        }
    }

    private static byte[] readFile(String filename) throws IOException {
        BufferedInputStream fin = new BufferedInputStream(new FileInputStream(filename));
        GZIPInputStream gzip = null;
        if (filename.endsWith(".gz")) {
            gzip = new GZIPInputStream(fin);
        }

        InputStream in = gzip != null ? gzip : fin;

        byte[] buffer = new byte[262144];
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }

        in.close();
        out.close();

        return out.toByteArray();
    }

}
