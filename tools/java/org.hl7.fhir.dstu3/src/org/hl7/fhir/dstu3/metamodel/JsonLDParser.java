package org.hl7.fhir.dstu3.metamodel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.dstu3.formats.IParser.OutputStyle;
import org.hl7.fhir.dstu3.formats.RdfGenerator.Complex;
import org.hl7.fhir.dstu3.formats.JsonCreator;
import org.hl7.fhir.dstu3.formats.JsonCreatorCanonical;
import org.hl7.fhir.dstu3.formats.JsonCreatorGson;
import org.hl7.fhir.dstu3.utils.IWorkerContext;
import org.hl7.fhir.utilities.Utilities;

public class JsonLDParser extends ParserBase {

	private JsonCreator json;

	public JsonLDParser(IWorkerContext context, boolean check) {
		super(context, check);
	}

	@Override
	public Element parse(InputStream stream) throws Exception {
		throw new NotImplementedException("not done yet");
	}


	protected void prop(String name, String value) throws IOException {
		if (name != null)
			json.name(name);
		json.value(value);
	}

	protected void open(String name) throws IOException {
		if (name != null) 
			json.name(name);
		json.beginObject();
	}

	protected void close() throws IOException {
		json.endObject();
	}

	protected void openArray(String name) throws IOException {
		if (name != null) 
			json.name(name);
		json.beginArray();
	}

	protected void closeArray() throws IOException {
		json.endArray();
	}


	@Override
	public void compose(Element e, OutputStream stream, OutputStyle style) throws Exception {
		OutputStreamWriter osw = new OutputStreamWriter(stream, "UTF-8");
		if (style == OutputStyle.CANONICAL)
			json = new JsonCreatorCanonical(osw);
		else
			json = new JsonCreatorGson(osw);
		json.setIndent(style == OutputStyle.PRETTY ? "  " : "");
		json.beginObject();
    prop("@context", "http://hl7.org/fhir/jsonld/"+e.getType());
		prop("resourceType", e.getType());
		Set<String> done = new HashSet<String>();
		for (Element child : e.getChildren()) {
			compose(e.getName(), e, done, child);
		}
		json.endObject();
		json.finish();
		osw.flush();
	}

	private void compose(String path, Element e, Set<String> done, Element child) throws IOException {
		if (!child.getProperty().isList()) {
			compose(path, child);
		} else if (!done.contains(child.getName())) {
			done.add(child.getName());
			List<Element> list = e.getChildrenByName(child.getName());
			composeList(path, list);
		}
	}

	private void composeList(String path, List<Element> list) throws IOException {
		// there will be at least one element
    String en = list.get(0).getProperty().getDefinition().getBase().getPath();
    boolean doType = false;
    if (en == null) {
      en = list.get(0).getProperty().getStructure().getName() +"."+list.get(0).getProperty().getDefinition().getPath().substring(list.get(0).getProperty().getDefinition().getPath().lastIndexOf(".")+1);
      if (en.endsWith("[x]")) {
        en = en.substring(0, en.length()-3);
        doType = true;        
      }
    }

    openArray(en);
    for (Element item : list) { 
      open(null);
      if (doType)
        prop("type", item.getType());
      if (item.getProperty().isPrimitive() || ParserBase.isPrimitive(item.getType())) {
        if (item.hasValue())
          primitiveValue(item);
      }
      Set<String> done = new HashSet<String>();
      for (Element child : item.getChildren()) {
        compose(path+"."+item.getName(), item, done, child);
      }
      close();
    }
    closeArray();
	}

	private void primitiveValue(Element item) throws IOException {
		json.name("value");
	  String type = item.getType();
	  if (Utilities.existsInList(type, "boolean"))
	  	json.value(item.getValue().equals("true") ? new Boolean(true) : new Boolean(false));
	  else if (Utilities.existsInList(type, "integer", "unsignedInt", "positiveInt"))
	  	json.value(new Integer(item.getValue()));
	  else if (Utilities.existsInList(type, "decimal"))
	  	json.value(new BigDecimal(item.getValue()));
	  else
	  	json.value(item.getValue());	
	}

	private void compose(String path, Element element) throws IOException {
    String en = element.getProperty().getDefinition().getBase().getPath();
    boolean doType = false;
    if (en == null) {
      en = element.getProperty().getStructure().getName() +"."+element.getProperty().getDefinition().getPath().substring(element.getProperty().getDefinition().getPath().lastIndexOf(".")+1);
      if (en.endsWith("[x]")) {
        en = en.substring(0, en.length()-3);
        doType = true;        
      }
    }

    if (element.hasChildren() || element.hasComments() || element.hasValue()) {
			open(en);
			if (doType)
        prop("type", element.getType());
			  
	    if (element.getProperty().isPrimitive() || ParserBase.isPrimitive(element.getType())) {
	      if (element.hasValue())
	        primitiveValue(element);
	    }
			if (element.getProperty().isResource()) {
		    prop("resourceType", element.getType());
				element = element.getChildren().get(0);
			}
			Set<String> done = new HashSet<String>();
			for (Element child : element.getChildren()) {
				compose(path+"."+element.getName(), element, done, child);
			}
	    if ("Coding".equals(element.getType()))
	      decorateCoding(element);
	    if ("CodeableConcept".equals(element.getType()))
	      decorateCodeableConcept(element);
			
			close();
		}
	}

  protected void decorateCoding(Element coding) throws IOException {
    String system = coding.getChildValue("system");
    String code = coding.getChildValue("code");
    
    if (system == null)
      return;
    if ("http://snomed.info/sct".equals(system)) {
      json.name("concept");
      json.value("http://snomed.info/sct#"+code);
    } else if ("http://loinc.org".equals(system)) {
      json.name("concept");
      json.value("http://loinc.org/owl#"+code);
    }  
  }

  private void decorateCodeableConcept(Element element) throws IOException {
    for (Element c : element.getChildren("coding")) {
      decorateCoding(c);
    }
  }
  	
}
