package(default_visibility = ["//visibility:public"])

java_library(
  name = "covidquerylib",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
    "@duckutil//:duckutil_lib",
    "@duckutil//:sql_lib",
    "@maven//:commons_pool_commons_pool",
    "@maven//:commons_dbcp_commons_dbcp",
    "@maven//:net_minidev_json_smart",
    "@maven//:com_google_guava_guava",
  ],
)

java_binary(
  name = "covidquery",
  main_class = "duck.covid19.CovidQuery",
  runtime_deps = [
    ":covidquerylib",
  ],
  jvm_flags = [
    "-Xmx6g",
  ],

)


