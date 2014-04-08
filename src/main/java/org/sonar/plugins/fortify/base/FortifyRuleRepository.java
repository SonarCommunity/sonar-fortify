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

import com.google.common.io.Closeables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class FortifyRuleRepository extends RuleRepository {
  private static final Logger LOG = LoggerFactory.getLogger(FortifyRuleRepository.class);

  private final FortifyServerConfiguration configuration;
  private final String language;

  FortifyRuleRepository(FortifyServerConfiguration configuration, String language) {
    super(FortifyConstants.fortifyRepositoryKey(language), language);
    setName("Fortify");
    this.configuration = configuration;
    this.language = language;
  }

  @Override
  public List<Rule> createRules() {
    List<File> files = new ArrayList<File>();
    XMLFileFilter xmlFileFilter = new XMLFileFilter();
    for (String location : this.configuration.getRulePackLocations()) {
      File file = new File(location);
      if (file.isDirectory()) {
        for (String rulePack : file.list(xmlFileFilter)) {
          files.add(new File(file, rulePack));
        }
      } else if (file.exists()) {
        files.add(file);
      } else {
        FortifyRuleRepository.LOG.warn("Ignore rulepack location: \"{}\", file is not found.", file);
      }
    }
    return parseRulePack(files);
  }

  private List<Rule> parseRulePack(Collection<File> files) {
    List<Rule> rules = new ArrayList<Rule>();
    for (File file : files) {
      try {
        InputStream stream = new FileInputStream(file);
        try {
          rules.addAll(new FortifyRulePackParser().parse(stream, this.language));
        } finally {
          Closeables.closeQuietly(stream);
        }
      } catch (IOException e) {
        FortifyRuleRepository.LOG.error("Unexpected error during the parse of " + file + ".", e);
      } catch (FortifyParseException e) {
        FortifyRuleRepository.LOG.error("Unexpected error during the parse of " + file + ".", e);
      }
    }
    return rules;
  }

  private static final class XMLFileFilter implements FilenameFilter {
    @Override
    public boolean accept(File dir, String name) {
      return name.endsWith(".xml");
    }
  }
}
