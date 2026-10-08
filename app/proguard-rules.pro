# jlatexmath loads its macro classes by name, so R8 must not rename or remove them.
-keep class org.scilab.forge.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**
