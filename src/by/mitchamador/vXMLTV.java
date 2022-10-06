package by.mitchamador;

import by.mitchamador.XMLTV.Channel;
import by.mitchamador.XMLTV.Programme;
import com.iptv.parser.M3UFile;
import com.iptv.parser.M3UHead;
import com.iptv.parser.M3UItem;
import com.iptv.parser.M3UToolSet;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
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
        long t1, t2;

        t1 = System.currentTimeMillis();

        XMLTV epg = new XMLTV();
        epg.setChannelsOnly(channelsOnly);


        List<M3UFile> listM3U = new ArrayList<M3UFile>();
        for (String file : filelistM3U) {
            M3UFile m3u = M3UToolSet.load(file);
            listM3U.add(m3u);

            if (debug) {
                ArrayList<String> sList = new ArrayList<String>();
                for (M3UItem item : m3u.getItems()) {
                    sList.add((item.getTvgName() != null ? (item.getTvgName().replaceAll("_", " ") + " - ") : "") + item.getChannelName());
                }
                //Collections.sort(sList);
                System.out.println("channel list of m3u file: " + file);
                for (String s : sList) {
                    System.out.println(s);
                }
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads <= 0 ? Runtime.getRuntime().availableProcessors() : threads);
        ArrayList<Future<XMLTV>> futures = new ArrayList<Future<XMLTV>>();

        for (final String file : filelistXmlTv) {
            futures.add(pool.submit(new Callable<XMLTV>() {
                @Override
                public XMLTV call() throws Exception {
                    XMLTV epg = new XMLTV();
                    epg.setChannelsOnly(channelsOnly);

                    try {
                        epg.setFilename(file);

                        Long t1 = System.currentTimeMillis();
                        epg.parseStax(file);
                        Long t2 = System.currentTimeMillis();

                        if (debug) {
                            System.out.println(file + " " + (double) (t2 - t1) / (double) 1000 + " sec, total channels: " + epg.getChannels().size() + ", total programmes: " + epg.getProgrammes().size());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return epg;
                }
            }));
        }
        pool.shutdown();

        ArrayList<XMLTV> list = new ArrayList<XMLTV>();

        for (Future<XMLTV> future : futures) {
            try {
                list.add(future.get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                System.out.println(e.getMessage());
                System.exit(1);
                //e.printStackTrace();
            }
        }

        t2 = System.currentTimeMillis();

        if (debug) {
            System.out.println("total time for all files: " + (double) (t2 - t1) / (double) 1000 + "\n");
            for (XMLTV x : list) {
                ArrayList<String> sList = new ArrayList<String>();
                for (Channel c : x.getChannels()) {
                    sList.add(c.getName());
                }
                Collections.sort(sList);
                System.out.println("channel list of file: " + x.getFilename());
                for (String s : sList) {
                    System.out.println(s);
                }
            }
        }

        for (XMLTV x : list) {
            epg.getChannels().addAll(x.getChannels());
            epg.getProgrammes().addAll(x.getProgrammes());
            if (!quiet && channelsOnly) {
                System.out.println("=== " + x.getFilename());
                for (Channel ch : x.getChannels()) {
                    System.out.println(ch.getName());
                }
            }
        }

        Collections.sort(epg.getChannels(), new Comparator<Channel>() {
            @Override
            public int compare(Channel o1, Channel o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });


        int countM3U = 0;

        HashSet<String> aliases = new HashSet<String>();
        HashSet<String> missed = new HashSet<String>();
        HashMap<String, Channel> channelsXML = new HashMap<String, Channel>();

        for (M3UFile m3u : listM3U) {
            M3UHead head = m3u.getHeader();

            for (M3UItem item : m3u.getItems()) {
                String nameM3U;
                if (item.getTvgName() != null) {
                    nameM3U = item.getTvgName().replaceAll("_", " ");
                } else {
                    nameM3U = item.getChannelName();
                }

                Channel matchedChannel = null;

                // match by tvg-id and channel id
                for (Iterator<Channel> channelIterator = epg.getChannels().iterator(); matchedChannel == null && channelIterator.hasNext(); ) {
                    Channel ch = channelIterator.next();
                    if (ch.getId().equalsIgnoreCase(item.getTvgId())) {
                        matchedChannel = ch;
                    }
                }

                // match by tvg-name and channel id
                for (Iterator<Channel> channelIterator = epg.getChannels().iterator(); matchedChannel == null && channelIterator.hasNext(); ) {
                    Channel ch = channelIterator.next();
                    if (ch.getId().equalsIgnoreCase(item.getTvgName())) {
                        matchedChannel = ch;
                    }
                }

                // match by m3u name and display-name
                for (Iterator<Channel> channelIterator = epg.getChannels().iterator(); matchedChannel == null && channelIterator.hasNext(); ) {
                    Channel ch = channelIterator.next();
                    for (String nameXML : ch.getNameList()) {
                        if (nameXML.equalsIgnoreCase(nameM3U)) {
                            matchedChannel = ch;
                            break;
                        }
                    }
                }

                // match by converted m3u name and converted display-name
                for (Iterator<Channel> channelIterator = epg.getChannels().iterator(); matchedChannel == null && channelIterator.hasNext(); ) {
                    Channel ch = channelIterator.next();
                    for (String nameXML : ch.getNameList()) {
                        if (getConvertedName(nameXML).equalsIgnoreCase(getConvertedName(nameM3U))) {
                            if (item.getTvgId() != null && !item.getTvgId().isEmpty()) {
                                aliases.add(nameM3U + ":" + nameXML);
                            } else {
                                aliases.add(nameM3U + ":id=\"" + ch.getId() + "\"");
                            }
                            matchedChannel = ch;
                            break;
                        }
                    }
                }

                if (matchedChannel != null) {
                    channelsXML.put(matchedChannel.getId(), matchedChannel);
                } else {
                    missed.add(nameM3U);
                }

                countM3U++;
            }
        }


        if (!quiet && !aliases.isEmpty()) {
            System.out.println("=== aliases ===");
            ArrayList<String> sList = new ArrayList<String>(aliases);
            Collections.sort(sList);
            for (String s : sList) {
                System.out.println(s);
            }
        }

        if (!quiet && !missed.isEmpty()) {
            System.out.println("=== missed channels ===");
            ArrayList<String> sList = new ArrayList<String>(missed);
            Collections.sort(sList);
            for (String s : sList) {
                System.out.println(s);
            }
        }

        if (!quiet) {
            System.out.println("total channels: m3u - " + countM3U + ", aliases - " + aliases.size() +
                    ", missed - " + missed.size() + ", xmltv - " + channelsXML.size());
        }

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
                saveFile(out, xml.toString().getBytes("UTF-8"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (debug) {
            System.out.println("done.");
        }

    }

    private void parseArgs(String[] args) {
        ArrayList<String> m3uList = new ArrayList<String>();
        ArrayList<String> xmlTvList = new ArrayList<String>();
        if (args != null) {
            int c = 0;
            while (c < args.length) {
                String arg = args[c].toLowerCase();
                if ("-u".equals(arg)) {
                    c++;
                    if (c < args.length) {
                        m3uList.add(args[c]);
                    }
                } else if ("-x".equals(arg)) {
                    c++;
                    if (c < args.length) {
                        xmlTvList.add(args[c]);
                    }
                } else if ("-j".equals(arg)) {
                    c++;
                    if (c < args.length) {
                        try {
                            threads = Integer.parseInt(args[c]);
                        } catch (NumberFormatException e) {
                        }
                    }
                } else if ("-o".equals(arg)) {
                    c++;
                    if (c < args.length) {
                        out = args[c];
                    }
                } else if ("-q".equals(arg)) {
                    quiet = true;
                } else if ("-c".equals(arg)) {
                    channelsOnly = true;
                } else if ("-d".equals(arg)) {
                    debug = true;
                }
                c++;
            }
        }
        filelistM3U = m3uList.toArray(new String[0]);
        filelistXmlTv = xmlTvList.toArray(new String[0]);
    }


    static String getConvertedName(String s) {
        try {
            return s.toLowerCase().replaceAll("[ _\\-\\(\\)]|[hd]", "");
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
        FileOutputStream fo = null;
        try {
            fo = new FileOutputStream(filePath);
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
        } catch (IOException e) {
        } finally {
            if (fo != null) fo.close();
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
