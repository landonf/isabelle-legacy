(*  Title:      Pure/ML/ml_profiling.ML
    Author:     Makarius

Profiling for Poly/ML 5.6.
*)

structure ML_Profiling =
struct

fun profile_time pr f x =
  PolyML.Profiling.profileStream pr PolyML.Profiling.ProfileTime f x;

fun profile_time_thread pr f x =
  PolyML.Profiling.profileStream pr PolyML.Profiling.ProfileTimeThisThread f x;

fun profile_allocations pr f x =
  PolyML.Profiling.profileStream pr PolyML.Profiling.ProfileAllocations f x;

end;