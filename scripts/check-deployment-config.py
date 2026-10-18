#!/usr/bin/env python3
"""Check deployment boundaries without requiring a running Docker daemon."""
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def compose_config(env_file, *files):
    command = ["docker", "compose", "--env-file", str(ROOT / env_file)]
    for file in files:
        command.extend(["-f", str(ROOT / file)])
    command.extend(["config", "--format", "json"])
    return json.loads(subprocess.check_output(command, cwd=ROOT, text=True))


def require(condition, message):
    if not condition:
        raise SystemExit("Deployment boundary check failed: " + message)


app = compose_config(".env.vds.example", "compose.vds.yml", "compose.vds.public.yml")
edge = compose_config("infra/edge/.env.example", "infra/edge/compose.yml")
require(app["name"] != edge["name"], "app and router must be separate projects")
require("caddy" not in app["services"], "app must not own the shared router")
require(set(edge["services"]) == {"caddy"}, "router must not own application services")
for config in (app, edge):
    network = config["networks"]["edge"]
    require(network["external"] and network["name"] == "edge", "edge network must be external")
for name, service in app["services"].items():
    require(("edge" in service["networks"]) == (name == "nginx"),
            "only the app web gateway may join edge: " + name)
    for port in service.get("ports", []):
        require(port.get("host_ip") == "127.0.0.1", "app ports must be loopback-only")
require("yesihave-web" in app["services"]["nginx"]["networks"]["edge"]["aliases"],
        "gateway needs a stable, unique routing alias")
nginx = (ROOT / "nginx/nginx-vds.conf").read_text()
backend_hosts = re.findall(r"proxy_pass\s+http://([^:/;]+):8080;", nginx)
require(backend_hosts and set(backend_hosts) == {app["name"] + "-app-1"},
        "gateway backend must use the project-qualified container name; app can collide on edge")
router = edge["services"]["caddy"]
require(set(router["networks"]) == {"edge"}, "router must not join backend networks")
require(not router.get("depends_on"), "router lifecycle must not depend on an app")
require({int(port["published"]) for port in router["ports"]} == {443},
        "router may publish only HTTPS; port 80 belongs to masterclass")
require(all("docker.sock" not in volume.get("source", "") for volume in router["volumes"]),
        "router must not receive Docker socket access")
print("Deployment boundaries passed: independent edge, private backends, HTTPS-only ingress")
