package ch.epfl.vlsc.analysis.partitioning.parser;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.reporting.Diagnostic;

import javax.xml.parsers.DocumentBuilderFactory;

public class DeviceProfileParser {


    private CompilationTask task;
    private Context context;

    private DeviceProfileDataBase db;

    public DeviceProfileParser(CompilationTask task, Context context) {
        this.task = task;
        this.context = context;
        this.db = new DeviceProfileDataBase();
    }

    public DeviceProfileDataBase getDataBase() {
        return this.db;
    }
    public void parseExecutionProfile(Path executionProfilePath) {
        try {

            Network network = task.getNetwork();

            File profXml = new File(executionProfilePath.toUri());

            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(profXml);

            doc.getDocumentElement().normalize();

            NodeList instanceList = doc.getElementsByTagName("actor");

            for (int instanceIx = 0; instanceIx < instanceList.getLength(); instanceIx ++) {

                Node instNode = instanceList.item(instanceIx);

                if (instNode.getNodeType() == Node.ELEMENT_NODE) {

                    Element instElem = (Element) instNode;
                    parseInstance(instElem, network);
                }
            }

        } catch (Exception e) {

        }

    }

    private void parseInstance(Element instance, Network network) {
        String instanceName = instance.getAttribute("id");
        Long ticks = Long.valueOf(instance.getAttribute("clockcycles-total"));

        network.getInstances().stream().forEach(inst -> {

            if (inst.getInstanceName().equals(instanceName)) {
                context.getReporter().report(
                        new Diagnostic(Diagnostic.Kind.INFO,
                                "Instance: " + instanceName + "  " +
                                        ticks.toString() + " ticks "));
                this.db.setInstanceTicks(inst, ticks);
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

                    NodeList conList = bwElem.getElementsByTagName("Connection");

                    for (int jx = 0; jx < conList.getLength(); jx++) {

                        Node conNode = conList.item(jx);
                        if (conNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element conElem = (Element) conNode;
                            // Read the time spent in ns
                            Long kernel_ns_total = Long.valueOf(conElem.getAttribute("kernel-total"));
                            Long read_ns_total = Long.valueOf(conElem.getAttribute("read-total"));
                            Long write_ns_total = Long.valueOf(conElem.getAttribute("write-total"));
                            Long bufferSize = Long.valueOf(conElem.getAttribute("buffer-size"));
                            this.db.setPCIeTicks(bufferSize,
                                    new DeviceProfileDataBase.PCIeTicks(read_ns_total,
                                            Long.valueOf(0),
                                            write_ns_total,
                                            kernel_ns_total,
                                            Long.valueOf(1)));

                        }
                    }
                }

            }


        } catch (Exception e){
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Could not open " + profilePath.toString()));
            e.printStackTrace();
        }
    }




}
