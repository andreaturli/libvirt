import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import java.util.UUID;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;
import org.libvirt.StoragePool;
import org.libvirt.StorageVol;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.jamesmurty.utils.XMLBuilder;

public class QEMU {

	public static void main(String[] args) {
		try {
			Connect conn = new Connect("qemu:///system", false);
			// Connect conn = new Connect("test:///default", false);

			String xmlDesc = "";
			String diskFileName = "";
			
			String[] domains = conn.listDefinedDomains();

			for (String domainName : domains) {
				Domain domain = conn.domainLookupByName(domainName);
				System.out.println(domain.getXMLDesc(0));
				if(domainName.equals("ttylinux")) {
					domain = conn.domainLookupByName(domainName);
					xmlDesc = domain.getXMLDesc(0);
					System.out.println("domain: " + domain.getUUIDString());
					
					XMLBuilder builder = XMLBuilder.parse(new InputSource(
							new StringReader(xmlDesc)));
	
					Document doc = builder.getDocument();
	
					XPathExpression expr = null;
					NodeList nodes = null;
					String xpathString = "//devices/disk[@device='disk']/source/@file"; // +
					expr = XPathFactory.newInstance().newXPath().compile(xpathString);
					nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
					diskFileName = nodes.item(0).getNodeValue();
					
					System.out.println("\n *** diskFileName " + diskFileName);
					
					StorageVol storageVol = conn.storageVolLookupByPath(diskFileName);
					System.out.println(storageVol.getXMLDesc(0));

					// cloning volume
					String poolName = "default";
					StoragePool storagePool = conn.storagePoolLookupByName(poolName );
					StorageVol clonedVol = cloneVolume(storagePool, storageVol);

					//System.out.println(generateClonedDomainXML(xmlDesc));
					conn.domainDefineXML(generateClonedDomainXML(xmlDesc));
				}
			}


			/*
			for (String poolName : conn.listStoragePools()) {
				System.out.println("> storage: " + poolName + "\n");

				StorageVol cloned = storagePool.storageVolCreateXMLFrom(clonedXML, storageVol, 0);
				System.out.println(cloned.getName());
			}

			String xmlStorageDesc = storageVol.getXMLDesc(0);
			System.out.println(storageVol.getKey());
			System.out.println(storageVol.getInfo().allocation);
			System.out.println(storageVol.getInfo().capacity);
			System.out.println(storageVol.getInfo().type);
			System.out.println(xmlStorageDesc);

*/


		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static StorageVol cloneVolume(StoragePool storagePool, StorageVol from) 
		throws LibvirtException, XPathExpressionException, ParserConfigurationException, SAXException, IOException, TransformerException {
		String fromXML = from.getXMLDesc(0);
		String clonedXML = generateClonedVolumeXML(fromXML);
		System.out.println(clonedXML);
		//return null;
		return storagePool.storageVolCreateXMLFrom(clonedXML, from, 0);
	}


	private static String generateClonedVolumeXML(String fromXML) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException, TransformerException {
		
		Properties outputProperties = new Properties();
		// Explicitly identify the output as an XML document
		outputProperties.put(javax.xml.transform.OutputKeys.METHOD, "xml");
		// Pretty-print the XML output (doesn't work in all cases)
		outputProperties.put(javax.xml.transform.OutputKeys.INDENT, "yes");
		// Get 2-space indenting when using the Apache transformer
		outputProperties.put("{http://xml.apache.org/xslt}indent-amount", "2");

		XMLBuilder builder = XMLBuilder.parse(new InputSource(new StringReader(fromXML)));

		String cloneAppend = "-clone";
		builder.xpathFind("//volume/name").t(cloneAppend);
		builder.xpathFind("//volume/key").t(cloneAppend);
		builder.xpathFind("//volume/target/path").t(cloneAppend);
		
		return builder.asString(outputProperties);
	}
	
	private static String generateClonedDomainXML(String fromXML) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException, TransformerException {
		
		Properties outputProperties = new Properties();
		// Explicitly identify the output as an XML document
		outputProperties.put(javax.xml.transform.OutputKeys.METHOD, "xml");
		// Pretty-print the XML output (doesn't work in all cases)
		outputProperties.put(javax.xml.transform.OutputKeys.INDENT, "yes");
		// Get 2-space indenting when using the Apache transformer
		outputProperties.put("{http://xml.apache.org/xslt}indent-amount", "2");

		XMLBuilder builder = XMLBuilder.parse(new InputSource(new StringReader(fromXML)));

		String cloneAppend = "-clone";
		
		builder.xpathFind("//domain/name").t(cloneAppend);
		// change uuid domain
		Element oldChild = builder.xpathFind("//domain/uuid").getElement();
		Node newNode = oldChild.cloneNode(true);
		newNode.getFirstChild().setNodeValue(UUID.randomUUID().toString());
		builder.getDocument().getDocumentElement().replaceChild(newNode, oldChild);
		
		builder.xpathFind("//domain/devices/disk/source").a("file", "/var/lib/libvirt/images/ttylinux.img-clone");
		builder.xpathFind("//domain/devices/interface/mac").a("address", "52:54:00:5c:dd:eb");
		return builder.asString(outputProperties);
	}

	
}
/*
 * XPathExpression expr =
 * XPathFactory.newInstance().newXPath().compile("//devices/disk[@device='disk']"
 * ); NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
 * for (int i = 0; i < nodes.getLength(); i++) { System.out.println("+++++ " +
 * nodes.item(i).getNodeName()); for(int j=0; j<
 * nodes.item(i).getChildNodes().getLength();j++)
 * if(nodes.item(i).getChildNodes().item(j).getNodeType() != 3) {
 * System.out.println("child: " +
 * nodes.item(i).getChildNodes().item(j).getNodeName()); NamedNodeMap map =
 * nodes.item(i).getChildNodes().item(j).getAttributes(); for (int k=0; k<
 * map.getLength(); k++) { System.out.println("\t" + map.item(k)); } }
 * 
 * 
 * il volume this.id = id; this.type = checkNotNull(type, "type"); this.size =
 * size; this.device = device; this.bootDevice = bootDevice; this.durable =
 * durable;
 * 
 * }
 */

//
//String xmlDesc2 = "<domain type='kvm'>"
//		+ "<name>test</name>"
//		+ "<uuid>abcf2039-a9f1-a659-7f91-e0f82f59d52e</uuid>"
//		+ "<memory>524288</memory>"
//		+ "<currentMemory>524288</currentMemory>"
//		+ "<vcpu>1</vcpu>"
//		+ "<os><type arch='i686' machine='pc-0.12'>hvm</type><boot dev='hd'/></os>"
//		+ "<features><acpi/>              <apic/>              <pae/>            </features>"
//		+ "<clock offset='utc'/>"
//		+ "<on_poweroff>destroy</on_poweroff>"
//		+ "<on_reboot>restart</on_reboot>"
//		+ "<on_crash>restart</on_crash>"
//		+ "<devices><emulator>/usr/bin/kvm</emulator><disk type='block' device='cdrom'>                <driver name='qemu' type='raw'/>                <target dev='hdc' bus='ide'/><readonly/></disk><disk type='file' device='disk'><driver name='qemu' type='raw'/><source file='/var/lib/libvirt/images/test.img'/>                <target dev='vda' bus='virtio'/>              </disk><disk type='file' device='disk'><driver name='qemu' type='raw'/><source file='/var/lib/libvirt/images/test1.img'/>                <target dev='vda' bus='virtio'/>              </disk> <interface type='network'>                <mac address='52:54:00:05:cf:92'/>                <source network='default'/>                <model type='virtio'/>              </interface>              <console type='pty'>                <target port='0'/>              </console>              <console type='pty'>                <target port='0'/>              </console>              <input type='mouse' bus='ps2'/>              <graphics type='vnc' port='-1' autoport='yes'/>              <video>                <model type='cirrus' vram='9216' heads='1'/>              </video> </devices>"
//		+ "</domain>";
//
//// System.out.println("//devices/disk/source: " +
//// builder.xpathFind("//devices/disk/source").getElement().getAttribute("file"));

