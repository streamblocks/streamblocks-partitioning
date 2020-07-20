package ch.epfl.vlsc.analysis.partitioning.parser;

import java.io.File;
import java.nio.file.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.reporting.Diagnostic;

import javax.xml.parsers.DocumentBuilderFactory;

public class DeviceProfileParser {


    private CompilationTask task;
    private Context context;

    private DeviceProfileDataBase db;

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


}
