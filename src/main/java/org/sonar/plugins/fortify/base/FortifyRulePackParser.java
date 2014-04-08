/*
 * SonarQube Fortify Plugin
 * Copyright (C) 2014 Vivien HENRIET and SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.fortify.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.Rule;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.annotation.CheckForNull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.sonar.plugins.fortify.base.FortifyParserUtils.getAtMostOneElementByTagName;
import static org.sonar.plugins.fortify.base.FortifyParserUtils.getSingleElementByTagName;

public class FortifyRulePackParser {
  private static final Logger LOG = LoggerFactory.getLogger(FortifyRulePackParser.class);

  @CheckForNull
  private String rulePackName = null;
  @CheckForNull
  private String rulePackLanguage = null;
  private final Map<String, Rule> rules = new HashMap<String, Rule>();
  private final Map<String, String> descriptions = new HashMap<String, String>();

  private static final Collection<String> INTERNAL_RULE_NAMES = new HashSet<String>(Arrays.asList(
    "DataflowSourceRule", "DataflowEntryPointRule", "DataflowPassthroughRule", "DataflowCleanseRule",
    "NonReturningRule", "MapRule", "DeprecationRule", "CharacterizationRule", "ControlflowActionPrototype",
    "ResultFilterRule", "GlobalFieldRule", "GlobalClassRule", "ScriptedCallGraphRule", "ControlflowTransition",
    "BufferCopyRule", "AllocationRule", "StringLengthRule"));

  private static final Collection<String> REAL_RULE_NAMES = new HashSet<String>(Arrays.asList(
    "DataflowSinkRule", "SemanticRule", "ControlflowRule", "StructuralRule", "InternalRule", "ConfigurationRule",
    "ContentRule"
    ));

  private Rule createRule(String language, String ruleID, String vulnCategory, String vulnSubcategory, String defaultSeverity, String description) {
    String name = vulnCategory;
    if (vulnSubcategory != null) {
      name += ": " + vulnSubcategory;
    }
    Rule rule = Rule.create(FortifyConstants.fortifyRepositoryKey(language), ruleID, name);
    rule.setDescription(description);
    rule.setLanguage(language);

    String severity;
    Double level = Double.valueOf(defaultSeverity);
    if (level >= 4.0) {
      severity = Severity.BLOCKER;
    } else if (level >= 3.0) {
      severity = Severity.CRITICAL;
    } else if (level >= 2.0) {
      severity = Severity.MAJOR;
    } else if (level >= 1.0) {
      severity = Severity.MINOR;
    } else {
      severity = Severity.INFO;
    }
    rule.setSeverity(org.sonar.api.rules.RulePriority.valueOf(severity));

    return rule;
  }

  private void handleRule(String language, Element element)
    throws FortifyParseException {
    String ruleLanguage = element.getAttribute("language");
    String ruleID = getSingleElementByTagName(element, "RuleID").getTextContent();
    if (ruleLanguage.length() == 0 || ruleLanguage.equals(language)) {
      String vulnCategory = getSingleElementByTagName(element, "VulnCategory").getTextContent();
      Element vulnSubcategoryElement = getAtMostOneElementByTagName(element, "VulnSubcategory");
      String vulnSubcategory = null;
      if (vulnSubcategoryElement != null) {
        vulnSubcategory = vulnSubcategoryElement.getTextContent();
      }
      String defaultSeverity = getSingleElementByTagName(element, "DefaultSeverity").getTextContent();
      Element description = getSingleElementByTagName(element, "Description");
      String descriptionKey = description.getAttribute("ref");
      String ruleDescription;
      if (descriptionKey.length() == 0) {
        ruleDescription = handleDescription(null, description).toHTML();
      } else {
        ruleDescription = this.descriptions.get(descriptionKey);
      }
      if (this.rules.put(ruleID, createRule(language, ruleID, vulnCategory, vulnSubcategory, defaultSeverity, ruleDescription)) != null) {
        FortifyRulePackParser.LOG.warn("The rule {} was already added, ignoring previous one.", ruleID, ruleLanguage);
      }
    } else {
      FortifyRulePackParser.LOG.info("Ignore rule {} as it is for {} language.", ruleID, ruleLanguage);
    }
  }

  private void handleRuleDefinition(String language, Element element)
    throws FortifyParseException {
    String name = element.getNodeName();

    if (FortifyRulePackParser.INTERNAL_RULE_NAMES.contains(name)) {
      // Flow analysis rules: ignore
    } else if (FortifyRulePackParser.REAL_RULE_NAMES.contains(name)) {
      handleRule(language, element);
    } else {
      FortifyRulePackParser.LOG.error("Rule of type: {} is unknown!", name);
    }

  }

  private void handleRuleDefinitions(String language, Element element)
    throws FortifyParseException {
    NodeList ruleDefinitions = element.getChildNodes();
    for (int i = 0; i < ruleDefinitions.getLength(); i++) {
      Node node = ruleDefinitions.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        handleRuleDefinition(language, (Element) node);
      }
    }
  }

  private void handleRules(String language, Element element)
    throws FortifyParseException {
    handleRuleDefinitions(language, getSingleElementByTagName(element, "RuleDefinitions"));
  }

  private void handleDescriptionReferences(FortifyRuleDescription description, Element references)
    throws FortifyParseException {
    NodeList referenceNodes = references.getElementsByTagName("Reference");
    for (int i = 0; i < referenceNodes.getLength(); i++) {
      Node node = referenceNodes.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element reference = (Element) node;
        String title = getSingleElementByTagName(reference, "Title").getTextContent();
        String author = null;
        Element authorElement = getAtMostOneElementByTagName(reference, "Author");
        if (authorElement != null) {
          author = authorElement.getTextContent();
        }
        description.addReference(title, author);
      }
    }
  }

  private FortifyRuleDescription handleDescription(@CheckForNull String id, Element element)
    throws FortifyParseException {
    Element abstractElement = getAtMostOneElementByTagName(element, "Abstract");
    String descriptionAbstract = null;
    if (abstractElement != null) {
      descriptionAbstract = abstractElement.getTextContent();
    }
    Element explanationElement = getAtMostOneElementByTagName(element, "Explanation");
    String explanation = null;
    if (explanationElement != null) {
      explanation = explanationElement.getTextContent();
    }
    Element recommendationsElement = getAtMostOneElementByTagName(element, "Recommendations");
    String recommendations = null;
    if (recommendationsElement != null) {
      recommendations = recommendationsElement.getTextContent();
    }
    FortifyRuleDescription description = new FortifyRuleDescription(id, descriptionAbstract, explanation, recommendations);
    Element references = getAtMostOneElementByTagName(element, "References");
    if (references != null) {
      handleDescriptionReferences(description, references);
    }
    return description;
  }

  private void handleDescriptionReference(Element element) throws FortifyParseException {
    String id = element.getAttribute("id");
    FortifyRuleDescription description = handleDescription(id, element);
    this.descriptions.put(description.getId(), description.toHTML());
  }

  private void handleDescriptions(Element element) throws FortifyParseException {
    NodeList descriptionNodes = element.getChildNodes();
    for (int i = 0; i < descriptionNodes.getLength(); i++) {
      Node node = descriptionNodes.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        handleDescriptionReference((Element) node);
      }
    }
  }

  private void handleRulePack(Element element) throws FortifyParseException {
    this.rulePackName = getSingleElementByTagName(element, "Name").getTextContent();
    Element languageElement = getAtMostOneElementByTagName(element, "Language");
    if (languageElement != null) {
      this.rulePackLanguage = languageElement.getTextContent();
    }
  }

  public Collection<Rule> parse(InputStream inputStream, String language) throws FortifyParseException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder documentBuilder = factory.newDocumentBuilder();
      Document document = documentBuilder.parse(inputStream);

      handleRulePack(getSingleElementByTagName(document, "RulePack"));
      if (this.rulePackLanguage == null || this.rulePackLanguage.equals(language)) {
        Element descriptionsElement = getAtMostOneElementByTagName(document, "Descriptions");
        if (descriptionsElement != null) {
          handleDescriptions(descriptionsElement);
        }
        handleRules(language, getSingleElementByTagName(document, "Rules"));
      } else {
        FortifyRulePackParser.LOG.info("Ignore rulepack {} as it is for {} language.", this.rulePackName, this.rulePackLanguage);
      }
    } catch (ParserConfigurationException e) {
      throw new FortifyParseException(e);
    } catch (SAXException e) {
      throw new FortifyParseException(e);
    } catch (IOException e) {
      throw new FortifyParseException(e);
    }
    return this.rules.values();
  }
}
