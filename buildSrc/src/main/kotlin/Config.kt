/*
 * Kiwix Android
 * Copyright (c) 2019 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

import org.gradle.api.JavaVersion

object Config {

  // Here is a list of all Android versions with their corresponding API
  // levels: https://apilevels.com/
  const val compileSdk = 33 // SDK version used by Gradle to compile our app.
  const val minSdk = 21 // Minimum SDK (Minimum Support Device) is 21 (Android 5.0 Lollipop).
  const val targetSdk = 33 // Target SDK (Maximum Support Device) is 33 (Android 13).

  val javaVersion = JavaVersion.VERSION_1_8
}
