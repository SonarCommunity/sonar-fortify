/*
 * Fortify Plugin for SonarQube
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
package org.sonar.fortify.fvdl;

import org.sonar.api.BatchExtension;
import org.sonar.api.config.Settings;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.fortify.base.FortifyConstants;

import java.util.Collection;

public class FortifySensorConfiguration implements BatchExtension {
  private final RulesProfile profile;
  private final Settings settings;

  public FortifySensorConfiguration(RulesProfile profile, Settings settings) {
    this.profile = profile;
    this.settings = settings;
  }

  public boolean isActive(Collection<String> languages) {
    int activeRuleCount = 0;
    for (String language : languages) {
      activeRuleCount += this.profile.getActiveRulesByRepository(FortifyConstants.fortifyRepositoryKey(language)).size();
    }
    return activeRuleCount > 0;
  }

  public String getReportPath() {
    return this.settings.getString(FortifyConstants.REPORT_PATH_PROPERTY);
  }

  public boolean hasNoSources() {
    return this.settings.getBoolean(FortifyConstants.NO_SOURCES_PROPERTY);
  }
}
