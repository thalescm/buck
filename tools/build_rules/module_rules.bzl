"""Contains build rules for Buck modules"""

load("@bazel_skylib//lib:sets.bzl", "sets")
load("//tools/build_rules:java_rules.bzl", "java_library_with_plugins")

def buck_module(
    name,
    **kwargs
):
    """Declares a buck module"""
    kwargs["provided_deps"] = sets.union(kwargs.get("provided_deps", []), [
        "//src/com/facebook/buck/module:module",
    ])

    java_library_with_plugins(
        name = name,
        **kwargs
    )

    jar_without_hash_name = name + '_jar_without_hash'

    native.java_binary(
        name = jar_without_hash_name,
        deps = [
            ":" + name,
        ],
    )

    calculate_module_hash_name = name + '_calculate_module_hash'

    native.genrule(
        name = calculate_module_hash_name,
        out = "module-binary-hash.txt",
        cmd = " ".join([
            "$(exe //py/hash:hash_files)",
            "$(location :{})".format(jar_without_hash_name),
            "$(location //py/hash:hash_files.py) > $OUT"
        ]),
    )

    native.genrule(
        name = name + "-module",
        out = "{}.jar".format(name),
        cmd = " ".join([
            "$(exe //py/buck/zip:append_with_copy)",
            "$(location :{}) $OUT".format(jar_without_hash_name),
            "META-INF/module-binary-hash.txt $(location :{})".format(calculate_module_hash_name)
        ]),
        visibility = [
            "//programs:bucklib",
            "//programs:calculate-buck-binary-hash",
            "//test/...",
        ],
    )

def get_module_binary(module):
  """ Returns target for module's binary """
  return "{}-module".format(module)

def convert_modules_to_resources(buck_modules):
  """ Converts modules to a map with resources for packaging in a Python binary """
  result = {}

  for k, v in buck_modules.items():
    result["buck-modules/{}.jar".format(k)] = get_module_binary(v)

  return result
