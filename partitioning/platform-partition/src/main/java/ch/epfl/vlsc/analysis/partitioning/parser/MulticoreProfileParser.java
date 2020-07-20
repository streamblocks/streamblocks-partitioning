package ch.epfl.vlsc.analysis.partitioning.parser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MulticoreProfileParser {

    private enum Status{
        EmptyDB,
        ParsedExecutionProfile,
        ParsedBandwidthProfile,
        ParsedBoth;
    };
    private Status state;
    private MulticoreProfileDataBase db;
    private CompilationTask task;
    private Context context;
    private Double clockPeriod;
    public MulticoreProfileParser(CompilationTask task, Context context, Double clockPeriod) {
       this.context = context;
       this.task = task;
       this.db = new MulticoreProfileDataBase();
       this.clockPeriod = clockPeriod;
       this.state = Status.EmptyDB;
    }


    public void parseExecutionProfile(Path executionProfilePath) {
        try {
            Network network = task.getNetwork();

            File profileXml = new File(executionProfilePath.toUri());
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(profileXml);
            // normalize the doc
            doc.getDocumentElement().normalize();


            NodeList instanceList = doc.getElementsByTagName("Instance");
            for (int instanceId = 0; instanceId < instanceList.getLength(); instanceId ++) {

                Node instNode = instanceList.item(instanceId);
                if (instNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element instElem = (Element) instNode;
                    parseInstance(instElem, network, context);


                }

            }

            NodeList connectionList = doc.getElementsByTagName("Connection");

            for (int connectionId = 0; connectionId < connectionList.getLength(); connectionId ++) {
                Node conNode = connectionList.item(connectionId);
                if (conNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element conElem = (Element) conNode;
                    parseConnection(conElem, network, context);
                }
            }



        } catch (Exception e) {
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Error parsing profile data "
                            + executionProfilePath.toString()));
            e.printStackTrace();
        }
        if (this.state == Status.EmptyDB)
            this.state = Status.ParsedExecutionProfile;
        else if (this.state == Status.ParsedBandwidthProfile)
            this.state = Status.ParsedBoth;
    }
    private void parseInstance(Element instance, Network network, Context context) {
        String strippedName = instance.getAttribute("actor-id");
        Long complexity = Long.valueOf(instance.getAttribute("complexity"));

        network.getInstances().stream().forEach(inst -> {

            if (inst.getInstanceName().equals(strippedName)) {
                context.getReporter().report(
                        new Diagnostic(Diagnostic.Kind.INFO,
                                "Instance: " + strippedName + "  " +
                                        complexity.toString() + " ticks "));

                this.db.setInstanceTicks(inst, complexity);
            }
        });
    }

    private void parseConnection(Element connection, Network network, Context context) {

        String source = connection.getAttribute("src");
        String sourcePort = connection.getAttribute("src-port");
        String target = connection.getAttribute("dst");
        String targetPort = connection.getAttribute("dst-port");
        Long count = Long.valueOf(connection.getAttribute("bandwidth"));
        Integer tokenSize = Integer.valueOf(connection.getAttribute("token-size"));
        Integer depth = Integer.valueOf(connection.getAttribute("size"));
        network.getConnections().forEach(con -> {
            if(con.getSource().getInstance().isPresent() && con.getTarget().getInstance().isPresent()) {
                if (con.getSource().getInstance().get().equals(source) && con.getSource().getPort().equals(sourcePort) &&
                        con.getTarget().getInstance().get().equals(target) && con.getTarget().getPort().equals(targetPort)) {

                    context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO,
                            String.format("Connection: %s.%s --> %s.%s  %d tokens",
                                    source, sourcePort, target, targetPort, count)));
                    this.db.setTokensExchanged(con, count);
                    this.db.setSettings(con, new MulticoreProfileDataBase.ConnectionSettings(depth, tokenSize));
                }
            }
        });

    }

    public void parseBandwidthProfile(Path profilePath) {
        try {

            File profileXml = new File(profilePath.toUri());
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(profileXml);
            // normalize the doc
            doc.getDocumentElement().normalize();

            NodeList bwList = doc.getElementsByTagName("bandwidth-test");
            Map<Integer, CommonProfileDataBase.CommunicationTicks.Builder> data = new HashMap<>();

            for(int ix = 0; ix < bwList.getLength(); ix++) {

                Node bwNode = bwList.item(ix);
                if (bwNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element bwElem = (Element) bwNode;
                    CommonProfileDataBase.CommunicationTicks.Kind kind =
                            bwElem.getAttribute("type").equals("single") ?
                                    CommonProfileDataBase.CommunicationTicks.Kind.Local :
                                    CommonProfileDataBase.CommunicationTicks.Kind.Global;

                    NodeList conList = bwElem.getElementsByTagName("Connection");

                    for (int jx = 0; jx < conList.getLength(); jx++) {

                        Node conNode = conList.item(jx);
                        if (conNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element conElem = (Element) conNode;
                            Long ticks = Long.valueOf(conElem.getAttribute("ticks"));
                            Integer repeats = Integer.valueOf(conElem.getAttribute("repeats"));
                            Integer bufferSize = Integer.valueOf(conElem.getAttribute("buffer-size"));

                            if (data.containsKey(bufferSize)) {

                                data.get(bufferSize).set(ticks, kind);
                            } else {
                                CommonProfileDataBase.CommunicationTicks.Builder bwBuilder =
                                        CommonProfileDataBase.CommunicationTicks.builder();
                                bwBuilder.set(ticks, kind);
                                data.put(bufferSize, bwBuilder);
                            }

                        }
                    }
                }

            }
            data.forEach((bufferSize, bw) -> this.db.setCommunicationTicks(bufferSize, bw.build()));

        } catch (Exception e) {
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Error parsing profile data "
                            + profilePath.toString()));
            e.printStackTrace();
        }
        this.db.getCommunicationProfileDataBase().forEach((bufferSize, bw) -> {
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, String.format("buffer size = %d -> inter: %d, intra: %d",
                            bufferSize,
                            bw.get(CommonProfileDataBase.CommunicationTicks.Kind.Global),
                            bw.get(CommonProfileDataBase.CommunicationTicks.Kind.Local))));

        });

        if (this.state == Status.EmptyDB)
            this.state = Status.ParsedBandwidthProfile;
        else if (this.state == Status.ParsedExecutionProfile)
            this.state = Status.ParsedBoth;
    }

    public MulticoreProfileDataBase getDataBase() {

        if (this.state == Status.ParsedBoth || this.state == Status.ParsedExecutionProfile) {
            return this.db;
        }
        else {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            "Illegal getDataBase(), parse status is " + this.state.toString()));

        }
    }


}
