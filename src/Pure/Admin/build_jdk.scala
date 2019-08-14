/*  Title:      Pure/Admin/build_jdk.scala
    Author:     Makarius

Build Isabelle jdk component from original platform installations.
*/

package isabelle


import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

import scala.util.matching.Regex


object Build_JDK
{
  /* version */

  sealed case class Version(short: String, full: String)

  def detect_version(s: String): Version =
  {
    val Version_Dir_Entry = """^jdk1\.(\d+)\.0_(\d+)(?:\.jdk)?$""".r
    s match {
      case Version_Dir_Entry(a, b) => Version(a + "u" + b, "1." + a + ".0_" + b)
      case _ => error("Cannot detect JDK version from " + quote(s))
    }
  }


  /* platform */

  sealed case class JDK_Platform(name: String, exe: String, regex: Regex)
  {
    override def toString: String = name

    def detect(jdk_dir: Path): Boolean =
    {
      val path = jdk_dir + Path.explode(exe)
      if (path.is_file) {
        val file_descr = Isabelle_System.bash("file -b " + File.bash_path(path)).check.out
        regex.pattern.matcher(file_descr).matches
      }
      else false
    }
  }
  val jdk_platforms =
    List(
      JDK_Platform("x86_64-linux", "bin/java", """.*ELF 64-bit.*x86[-_]64.*""".r),
      JDK_Platform("x86_64-windows", "bin/java.exe", """.*PE32\+ executable.*x86[-_]64.*""".r),
      JDK_Platform("x86_64-darwin", "Contents/Home/bin/java", """.*Mach-O 64-bit.*x86[-_]64.*""".r))


  /* README */

  def readme(version: Version): String =
"""This is JDK/JRE """ + version.full + """ as required for Isabelle.

See https://www.oracle.com/technetwork/java/javase/downloads/index.html
for the original downloads, which are covered by the Oracle Binary
Code License Agreement for Java SE.

Linux, Windows, Mac OS X all work uniformly, depending on certain
platform-specific subdirectories.
"""


  /* settings */

  val settings =
"""# -*- shell-script -*- :mode=shellscript:

case "$ISABELLE_PLATFORM_FAMILY" in
  linux)
    ISABELLE_JAVA_PLATFORM="$ISABELLE_PLATFORM64"
    ISABELLE_JDK_HOME="$COMPONENT/$ISABELLE_JAVA_PLATFORM"
    ;;
  freebsd)
    ISABELLE_JAVA_PLATFORM="$ISABELLE_PLATFORM64"
    ISABELLE_JDK_HOME="$COMPONENT/$ISABELLE_JAVA_PLATFORM"
    ;;
  windows)
    ISABELLE_JAVA_PLATFORM="$ISABELLE_WINDOWS_PLATFORM64"
    ISABELLE_JDK_HOME="$COMPONENT/$ISABELLE_JAVA_PLATFORM"
    ;;
  macos)
    ISABELLE_JAVA_PLATFORM="$ISABELLE_PLATFORM64"
    ISABELLE_JDK_HOME="$COMPONENT/$ISABELLE_JAVA_PLATFORM/Contents/Home"
    ;;
esac
"""


  /* extract archive */

  def extract_archive(dir: Path, archive: Path): (Version, JDK_Platform) =
  {
    try {
      val tmp_dir = dir + Path.explode("tmp")
      Isabelle_System.mkdirs(tmp_dir)
      Isabelle_System.gnutar(
        "-C " + File.bash_path(tmp_dir) + " -xzf " + File.bash_path(archive)).check
      val dir_entry =
        File.read_dir(tmp_dir) match {
          case List(s) => s
          case _ => error("Archive contains multiple directories")
        }
      val version = detect_version(dir_entry)

      val jdk_dir = tmp_dir + Path.explode(dir_entry)
      val platform =
        jdk_platforms.find(_.detect(jdk_dir)) getOrElse error("Failed to detect JDK platform")

      val platform_dir = dir + Path.explode(platform.name)
      if (platform_dir.is_dir) error("Directory already exists: " + platform_dir)
      File.move(jdk_dir, platform_dir)

      (version, platform)
    }
    catch { case ERROR(msg) => cat_error(msg, "The error(s) above occurred for " + archive) }
  }


  /* build jdk */

  def build_jdk(
    archives: List[Path],
    progress: Progress = No_Progress,
    target_dir: Path = Path.current)
  {
    if (Platform.is_windows) error("Cannot build jdk on Windows")

    Isabelle_System.with_tmp_dir("jdk")(dir =>
      {
        progress.echo("Extracting ...")
        val extracted = archives.map(extract_archive(dir, _))

        val version =
          extracted.map(_._1).toSet.toList match {
            case List(version) => version
            case Nil => error("No archives")
            case versions =>
              error("Archives contain multiple JDK versions: " +
                commas_quote(versions.map(_.short)))
          }

        val missing_platforms =
          jdk_platforms.filterNot(p1 => extracted.exists({ case (_, p2) => p1.name == p2.name }))
        if (missing_platforms.nonEmpty)
          error("Missing platforms: " + commas_quote(missing_platforms.map(_.name)))

        val jdk_name = "jdk-" + version.short
        val jdk_path = Path.explode(jdk_name)
        val component_dir = dir + jdk_path

        Isabelle_System.mkdirs(component_dir + Path.explode("etc"))
        File.write(component_dir + Path.explode("etc/settings"), settings)
        File.write(component_dir + Path.explode("README"), readme(version))

        for ((_, platform) <- extracted)
          File.move(dir + Path.explode(platform.name), component_dir)

        for (file <- File.find_files(component_dir.file, include_dirs = true)) {
          val path = file.toPath
          val perms = Files.getPosixFilePermissions(path)
          perms.add(PosixFilePermission.OWNER_READ)
          perms.add(PosixFilePermission.GROUP_READ)
          perms.add(PosixFilePermission.OTHERS_READ)
          perms.add(PosixFilePermission.OWNER_WRITE)
          if (file.isDirectory) {
            perms.add(PosixFilePermission.OWNER_WRITE)
            perms.add(PosixFilePermission.OWNER_EXECUTE)
            perms.add(PosixFilePermission.GROUP_EXECUTE)
            perms.add(PosixFilePermission.OTHERS_EXECUTE)
          }
          Files.setPosixFilePermissions(path, perms)
        }

        File.find_files((component_dir + Path.explode("x86_64-darwin")).file,
          file => file.getName.startsWith("._")).foreach(_.delete)

        progress.echo("Sharing ...")
        val main_dir :: other_dirs =
          jdk_platforms.map(platform => (component_dir + Path.explode(platform.name)).file.toPath)
        for {
          file1 <- File.find_files(main_dir.toFile).iterator
          path1 = file1.toPath
          dir2 <- other_dirs.iterator
        } {
          val path2 = dir2.resolve(main_dir.relativize(path1))
          val file2 = path2.toFile
          if (!Files.isSymbolicLink(path2) && file2.isFile && File.eq_content(file1, file2)) {
            file2.delete
            Files.createLink(path2, path1)
          }
        }

        progress.echo("Archiving ...")
        Isabelle_System.gnutar("--owner=root --group=root -C " + File.bash_path(dir) +
          " -czf " + File.bash_path(target_dir + jdk_path.ext("tar.gz")) + " " + jdk_name).check
      })
  }


  /* Isabelle tool wrapper */

  val isabelle_tool =
    Isabelle_Tool("build_jdk", "build Isabelle jdk component from original platform installations",
    args =>
    {
      var target_dir = Path.current

      val getopts = Getopts("""
Usage: isabelle build_jdk [OPTIONS] ARCHIVES...

  Options are:
    -D DIR       target directory (default ".")

  Build jdk component from tar.gz archives, with original jdk installations
  for x86_64 Linux, Windows, Mac OS X.
""",
        "D:" -> (arg => target_dir = Path.explode(arg)))

      val more_args = getopts(args)
      if (more_args.isEmpty) getopts.usage()

      val archives = more_args.map(Path.explode(_))
      val progress = new Console_Progress()

      build_jdk(archives = archives, progress = progress, target_dir = target_dir)
    }, admin = true)
}
