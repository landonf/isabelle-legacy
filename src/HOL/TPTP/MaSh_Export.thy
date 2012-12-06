(*  Title:      HOL/TPTP/MaSh_Export.thy
    Author:     Jasmin Blanchette, TU Muenchen
*)

header {* MaSh Exporter *}

theory MaSh_Export
imports Main
begin

ML_file "mash_export.ML"

sledgehammer_params
  [provers = spass, max_relevant = 32, strict, dont_slice, type_enc = mono_native,
   lam_trans = combs_and_lifting, timeout = 2, dont_preplay, minimize]

ML {*
open MaSh_Export
*}

ML {*
val do_it = false (* switch to "true" to generate the files *)
val thys = [@{theory List}]
val params as {provers, ...} = Sledgehammer_Isar.default_params @{context} []
val prover = hd provers
val dir = space_implode "__" (map Context.theory_name thys)
val prefix = "/tmp/" ^ dir ^ "/"
*}

ML {*
if do_it then
  Isabelle_System.mkdir (Path.explode prefix)
else
  ()
*}

ML {*
if do_it then
  generate_accessibility @{context} thys false (prefix ^ "mash_accessibility")
else
  ()
*}

ML {*
if do_it then
  generate_features @{context} prover thys false (prefix ^ "mash_features")
else
  ()
*}

ML {*
if do_it then
  generate_isar_dependencies @{context} thys false (prefix ^ "mash_dependencies")
else
  ()
*}

ML {*
if do_it then
  generate_isar_commands @{context} prover thys (prefix ^ "mash_commands")
else
  ()
*}

ML {*
if do_it then
  generate_mepo_suggestions @{context} params thys 1024 (prefix ^ "mash_mepo_suggestions")
else
  ()
*}

ML {*
if do_it then
  generate_atp_dependencies @{context} params thys false (prefix ^ "mash_atp_dependencies")
else
  ()
*}

ML {*
if do_it then
  generate_atp_commands @{context} params thys (prefix ^ "mash_atp_commands")
else
  ()
*}

end
