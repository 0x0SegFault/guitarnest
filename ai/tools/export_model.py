"""Converts the Basic Pitch ONNX model to models/basic-pitch.gnm, read by the app's own small interpreter
(shared/src/commonMain/kotlin/app/guitarnest/listen/Graph.kt). No ONNX runtime is needed on the phone.

Run: python ai/tools/export_model.py ai/models/nmp.onnx models/basic-pitch.gnm   (needs: pip install onnx)

File: "GNM1", uint32 little-endian length of a JSON header, the header, then float32 little-endian values.
"""
import json
import struct
import sys

import numpy as np
import onnx
from onnx import helper, numpy_helper


def main(src, dst):
    m = onnx.load(src)
    g = m.graph
    blobs, tensors, offset = [], {}, 0
    for t in g.initializer:
        a = numpy_helper.to_array(t)
        flat = a.astype(np.float32).ravel()
        tensors[t.name] = {"shape": list(a.shape), "offset": offset, "length": int(flat.size)}
        blobs.append(flat)
        offset += flat.size
    nodes = []
    for n in g.node:
        attrs = {}
        for a in n.attribute:
            v = helper.get_attribute_value(a)
            if isinstance(v, bytes):
                v = v.decode()
            elif isinstance(v, (list, tuple)):
                v = [int(x) if isinstance(x, (int, np.integer)) else float(x) for x in v]
            elif isinstance(v, np.integer):
                v = int(v)
            elif isinstance(v, onnx.TensorProto):
                v = numpy_helper.to_array(v).astype(np.float32).ravel().tolist()
            attrs[a.name] = v
        nodes.append({"op": n.op_type, "in": list(n.input), "out": list(n.output), "attrs": attrs})
    header = {
        "source": "Basic Pitch 0.4.0 (Spotify, Apache-2.0), icassp_2022/nmp.onnx",
        "inputs": [i.name for i in g.input],
        "outputs": [o.name for o in g.output],
        "nodes": nodes,
        "tensors": tensors,
    }
    data = json.dumps(header, separators=(",", ":")).encode()
    with open(dst, "wb") as f:
        f.write(b"GNM1")
        f.write(struct.pack("<I", len(data)))
        f.write(data)
        f.write(np.concatenate(blobs).astype("<f4").tobytes())
    print(dst, len(nodes), "nodes,", offset, "values")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
