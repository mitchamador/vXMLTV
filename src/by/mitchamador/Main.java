package by.mitchamador;

import com.iptv.parser.M3UFile;
import com.iptv.parser.M3UHead;
import com.iptv.parser.M3UItem;
import com.iptv.parser.M3UToolSet;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import by.mitchamador.XMLTV.Channel;
import by.mitchamador.XMLTV.Programme;
import by.mitchamador.XMLTV.DisplayName;

public class Main {

    static String[] filelistM3U;
    static String[] filelistXmlTv;

    static String out;
    static boolean quiet;
    static boolean debug;
    static int threads = 1;
    static boolean channelsOnly;

    static void parseArgs(String[] args) {
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
        filelistM3U = m3uList.toArray(new String[m3uList.size()]);
        filelistXmlTv = xmlTvList.toArray(new String[xmlTvList.size()]);
    }


    static String getConvertedName(String s) {
        return s.toLowerCase().replaceAll("[ _\\-\\(\\)]", "");
    }

    public static void main(String[] args) {
        long t1, t2;

        parseArgs(args);

        ExecutorService pool = Executors.newFixedThreadPool(threads <= 0 ? Runtime.getRuntime().availableProcessors() : threads);

        t1 = new Date().getTime();

        ArrayList<Future<XMLTV>> futures = new ArrayList<Future<XMLTV>>();

        for (final String file : filelistXmlTv) {
            futures.add(pool.submit(new Callable<XMLTV>() {
                @Override
                public XMLTV call() throws Exception {
                    XMLTV epg = new XMLTV();

                    try {
                        epg.filename = file;

                        Long t1 = new Date().getTime();
                        epg.parseStax(file);
                        Long t2 = new Date().getTime();

                        if (debug) {
                            System.out.println(file + " " + (double) (t2 - t1) / (double) 1000 + " sec, total channels: " + epg.channels.size() + ", total programmes: " + epg.programmes.size());
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

        t2 = new Date().getTime();

        if (debug) {
            System.out.println("total time for all files: " + (double) (t2 - t1) / (double) 1000 + "\n");
            for (XMLTV x : list) {
                ArrayList<String> sList = new ArrayList<String>();
                for (Channel c : x.channels) {
                    sList.add(c.getName());
                }
                Collections.sort(sList);
                System.out.println("channel list of file: " + x.filename);
                for (String s : sList) {
                    System.out.println(s);
                }
            }
        }

        XMLTV epg = new XMLTV();

        for (XMLTV x : list) {
            epg.channels.addAll(x.channels);
            epg.programmes.addAll(x.programmes);
            if (!quiet && channelsOnly) {
                System.out.println("=== " + x.filename);
                for (Channel ch : x.channels) {
                    System.out.println(ch.getName());
                }
            }
        }

        Collections.sort(epg.channels, new Comparator<Channel>() {
            @Override
            public int compare(Channel o1, Channel o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        int countM3U = 0;

        HashSet<String> aliases = new HashSet<String>();
        HashSet<String> missed = new HashSet<String>();
        HashMap<String, Channel> channelsXML = new HashMap<String, Channel>();

        for (String file : filelistM3U) {
            M3UFile m3u = M3UToolSet.load(file);

            if (debug) {
                ArrayList<String> sList = new ArrayList<String>();
                for (M3UItem item : m3u.getItems()) {
                    sList.add(item.getTvgName() != null ? item.getTvgName().replaceAll("_", " ") : item.getChannelName());
                }
                //Collections.sort(sList);
                System.out.println("channel list of m3u file: " + file);
                for (String s : sList) {
                    System.out.println(s);
                }
            }

            M3UHead head = m3u.getHeader();

            for (M3UItem item : m3u.getItems()) {
                String nameM3U;
                if (item.getTvgName() != null) {
                    nameM3U = item.getTvgName().replaceAll("_", " ");
                } else {
                    nameM3U = item.getChannelName();
                }

                boolean find = false;
                for (Channel ch : epg.channels) {
                    for (String nameXML : ch.getNameList()) {
                        if (nameXML.equals(nameM3U)) {
                            find = true;
                        } else if (getConvertedName(nameXML).equals(getConvertedName(nameM3U))) {
                            find = true;
                            if (!aliases.contains(nameM3U + ":" + nameXML)) {
                                aliases.add(nameM3U + ":" + nameXML);
                            }
                        }
                        if (find) {
                            channelsXML.put(ch.id, ch);
                            break;
                        }
                    }
                    if (find) {
                        break;
                    }
                }

                if (!find) {
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
            for (Programme p : epg.programmes) {
                Channel ch = channelsXML.get(p.channel);
                if (ch != null && p.start != null && p.stop != null) {
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
