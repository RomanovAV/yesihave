#!/usr/bin/env python3
import argparse
import os

import open_clip
import torch


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="ViT-B-32")
    parser.add_argument("--pretrained", default="openai")
    parser.add_argument("--output", default="models/clip-vitb32.onnx")
    parser.add_argument("--opset", type=int, default=17)
    args = parser.parse_args()

    device = "cpu"
    model, _, _ = open_clip.create_model_and_transforms(
        args.model, pretrained=args.pretrained
    )
    model.eval()
    model.to(device)

    class ImageEncoder(torch.nn.Module):
        def __init__(self, clip_model):
            super().__init__()
            self.clip_model = clip_model

        def forward(self, image):
            return self.clip_model.encode_image(image)

    encoder = ImageEncoder(model).to(device).eval()

    dummy = torch.randn(1, 3, 224, 224, device=device)
    output_dir = os.path.dirname(args.output)
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)

    torch.onnx.export(
        encoder,
        dummy,
        args.output,
        input_names=["image"],
        output_names=["embedding"],
        dynamic_axes={"image": {0: "batch"}, "embedding": {0: "batch"}},
        opset_version=args.opset,
    )

    print(f"ONNX exported to: {args.output}")
    print("Input name: image")
    print("Output name: embedding")
    print("Embedding dim: 512")


if __name__ == "__main__":
    main()
