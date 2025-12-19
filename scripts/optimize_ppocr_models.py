#!/usr/bin/env python3
"""
PP-OCRv4 Model Optimization Script for Paddle Lite
This script downloads and optimizes PP-OCRv4 models for Android deployment.

Requirements:
    pip install paddlelite paddlepaddle
"""

import os
import sys
import urllib.request
import tarfile
import shutil
from pathlib import Path

# Model URLs - Version selection
# Set OCR_VERSION env var: v3 (default, stable), v4, or v5 (latest)
OCR_VERSION = os.environ.get('OCR_VERSION', 'v3').lower()
USE_HUGGINGFACE = os.environ.get('USE_HF', 'false').lower() == 'true'

if OCR_VERSION == 'v5':
    print("Using PP-OCRv5 models (latest, may have Paddle Lite compatibility issues)")
    if USE_HUGGINGFACE:
        print("  Source: Hugging Face (direct download)")
        MODELS = {
            'detection': {
                'url': 'https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det',
                'name': 'PP-OCRv5_mobile_det',
                'output': 'ppocr_det_v5',
                'is_hf_repo': True
            },
            'recognition': {
                'url': 'https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec',
                'name': 'PP-OCRv5_mobile_rec',
                'output': 'ppocr_rec_v5',
                'is_hf_repo': True
            },
            'classifier': {
                'url': 'https://paddleocr.bj.bcebos.com/dygraph_v2.0/ch/ch_ppocr_mobile_v2.0_cls_infer.tar',
                'name': 'ch_ppocr_mobile_v2.0_cls_infer',
                'output': 'ppocr_cls',
                'is_hf_repo': False
            }
        }
    else:
        print("  Source: Baidu BOS (legacy - use USE_HF=true for Hugging Face)")
        MODELS = {
            'detection': {
                'url': 'https://paddleocr.bj.bcebos.com/PP-OCRv5/chinese/ch_PP-OCRv5_det_infer.tar',
                'name': 'ch_PP-OCRv5_det_infer',
                'output': 'ppocr_det_v5',
                'is_hf_repo': False
            },
            'recognition': {
                'url': 'https://paddleocr.bj.bcebos.com/PP-OCRv5/chinese/ch_PP-OCRv5_rec_infer.tar',
                'name': 'ch_PP-OCRv5_rec_infer',
                'output': 'ppocr_rec_v5',
                'is_hf_repo': False
            },
            'classifier': {
                'url': 'https://paddleocr.bj.bcebos.com/dygraph_v2.0/ch/ch_ppocr_mobile_v2.0_cls_infer.tar',
                'name': 'ch_ppocr_mobile_v2.0_cls_infer',
                'output': 'ppocr_cls',
                'is_hf_repo': False
            }
        }
elif OCR_VERSION == 'v4':
    print("Using PP-OCRv4 models (better accuracy, from Hugging Face)")
    if USE_HUGGINGFACE:
        print("  Source: Hugging Face (direct download)")
        MODELS = {
            'detection': {
                'url': 'https://huggingface.co/PaddlePaddle/PP-OCRv4_mobile_det',
                'name': 'PP-OCRv4_mobile_det',
                'output': 'ppocr_det_v4',
                'is_hf_repo': True
            },
            'recognition': {
                'url': 'https://huggingface.co/PaddlePaddle/PP-OCRv4_mobile_rec',
                'name': 'PP-OCRv4_mobile_rec',
                'output': 'ppocr_rec_v4',
                'is_hf_repo': True
            },
            'classifier': {
                'url': 'https://paddleocr.bj.bcebos.com/dygraph_v2.0/ch/ch_ppocr_mobile_v2.0_cls_infer.tar',
                'name': 'ch_ppocr_mobile_v2.0_cls_infer',
                'output': 'ppocr_cls',
                'is_hf_repo': False
            }
        }
    else:
        print("  Source: Baidu BOS (legacy - use USE_HF=true for Hugging Face)")
        MODELS = {
            'detection': {
                'url': 'https://paddleocr.bj.bcebos.com/PP-OCRv4/chinese/ch_PP-OCRv4_det_infer.tar',
                'name': 'ch_PP-OCRv4_det_infer',
                'output': 'ppocr_det_v4',
                'is_hf_repo': False
            },
            'recognition': {
                'url': 'https://paddleocr.bj.bcebos.com/PP-OCRv4/chinese/ch_PP-OCRv4_rec_infer.tar',
                'name': 'ch_PP-OCRv4_rec_infer',
                'output': 'ppocr_rec_v4',
                'is_hf_repo': False
            },
            'classifier': {
                'url': 'https://paddleocr.bj.bcebos.com/dygraph_v2.0/ch/ch_ppocr_mobile_v2.0_cls_infer.tar',
                'name': 'ch_ppocr_mobile_v2.0_cls_infer',
                'output': 'ppocr_cls',
                'is_hf_repo': False
            }
        }
else:  # v3 (default)
    print("Using PP-OCRv3 models (stable, fully compatible with Paddle Lite)")
    if USE_HUGGINGFACE:
        print("  Source: Hugging Face")
        MODELS = {
            'detection': {
                'url': 'https://huggingface.co/PaddlePaddle/ch_PP-OCRv3_det_infer/resolve/main/inference.tar',
                'name': 'ch_PP-OCRv3_det_infer',
                'output': 'ppocr_det_v3'
            },
            'recognition': {
                'url': 'https://huggingface.co/PaddlePaddle/ch_PP-OCRv3_rec_infer/resolve/main/inference.tar',
                'name': 'ch_PP-OCRv3_rec_infer',
                'output': 'ppocr_rec_v3'
            },
            'classifier': {
                'url': 'https://paddleocr.bj.bcebos.com/dygraph_v2.0/ch/ch_ppocr_mobile_v2.0_cls_infer.tar',
                'name': 'ch_ppocr_mobile_v2.0_cls_infer',
                'output': 'ppocr_cls'
            }
        }
    else:
        print("  Source: Baidu BOS (fastest)")
        MODELS = {
            'detection': {
                'url': 'https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_det_infer.tar',
                'name': 'ch_PP-OCRv3_det_infer',
                'output': 'ppocr_det_v3'
            },
            'recognition': {
                'url': 'https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_rec_infer.tar',
                'name': 'ch_PP-OCRv3_rec_infer',
                'output': 'ppocr_rec_v3'
            },
            'classifier': {
                'url': 'https://paddleocr.bj.bcebos.com/dygraph_v2.0/ch/ch_ppocr_mobile_v2.0_cls_infer.tar',
                'name': 'ch_ppocr_mobile_v2.0_cls_infer',
                'output': 'ppocr_cls'
            }
        }

def download_hf_model(repo_url, model_name, extract_path):
    """Download model files directly from Hugging Face repository"""
    print(f"Downloading from Hugging Face: {repo_url}...")
    
    # Create model directory
    model_dir = os.path.join(extract_path, model_name)
    os.makedirs(model_dir, exist_ok=True)
    
    # Download inference.pdmodel and inference.pdiparams
    # Note: Hugging Face models store these as inference.pdmodel (not .pdmodel with json extension)
    base_url = f"{repo_url}/resolve/main"
    files = ['inference.pdiparams', 'inference.json']
    
    for filename in files:
        file_url = f"{base_url}/{filename}"
        output_path = os.path.join(model_dir, filename)
        print(f"  Downloading {filename}...")
        try:
            urllib.request.urlretrieve(file_url, output_path)
            size_mb = os.path.getsize(output_path) / (1024 * 1024)
            print(f"  ✓ Downloaded {filename} ({size_mb:.2f} MB)")
        except Exception as e:
            print(f"  ✗ Failed to download {filename}: {e}")
            raise
    
    # Rename inference.json to inference.pdmodel for Paddle Lite
    json_path = os.path.join(model_dir, 'inference.json')
    model_path = os.path.join(model_dir, 'inference.pdmodel')
    if os.path.exists(json_path):
        shutil.copy2(json_path, model_path)
        print(f"  ✓ Created inference.pdmodel from inference.json")
    
    print(f"✓ Downloaded model to {model_dir}")

def download_and_extract(url, extract_path):
    """Download and extract tar file"""
    print(f"Downloading from {url}...")
    filename = url.split('/')[-1]
    
    # Download
    urllib.request.urlretrieve(url, filename)
    print(f"Downloaded {filename}")
    
    # Extract
    print(f"Extracting {filename}...")
    with tarfile.open(filename, 'r') as tar:
        tar.extractall(extract_path)
    
    # Clean up tar file
    os.remove(filename)
    print(f"Extracted to {extract_path}")

def optimize_model(model_dir, model_name, output_name):
    """Optimize model using paddle_lite_opt"""
    # Try both naming conventions
    model_file = os.path.join(model_dir, model_name, "inference.pdmodel")
    param_file = os.path.join(model_dir, model_name, "inference.pdiparams")
    
    # Fallback to old naming convention
    if not os.path.exists(model_file):
        model_file = os.path.join(model_dir, model_name, f"{model_name}.pdmodel")
        param_file = os.path.join(model_dir, model_name, f"{model_name}.pdiparams")
    
    output_file = os.path.join('optimized_models', output_name)
    
    if not os.path.exists(model_file):
        print(f"Error: Model file not found: {model_file}")
        return False
    
    print(f"\nOptimizing {model_name}...")
    print(f"  Model: {model_file}")
    print(f"  Params: {param_file}")
    print(f"  Output: {output_file}.nb")
    
    # Run paddle_lite_opt with additional flags for v4 compatibility
    cmd = (
        f"paddle_lite_opt "
        f"--model_file={model_file} "
        f"--param_file={param_file} "
        f"--optimize_out={output_file} "
        f"--valid_targets=arm "
        f"--optimize_out_type=naive_buffer "
        f"--enable_fp16=true"
    )
    
    result = os.system(cmd)
    
    if result == 0:
        print(f"✓ Successfully optimized to {output_file}.nb")
        return True
    else:
        print(f"✗ Failed to optimize {model_name}")
        return False

def download_ppocr_dict():
    """Download the PP-OCR dictionary file"""
    # Using dict.txt which includes English characters
    dict_url = "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/en_dict.txt"
    dict_file = "optimized_models/ppocr_keys_v1.txt"
    
    print(f"\nDownloading dictionary file...")
    try:
        urllib.request.urlretrieve(dict_url, dict_file)
        print(f"✓ Dictionary saved to {dict_file}")
    except Exception as e:
        print(f"Warning: Could not download dictionary: {e}")
        print("Creating basic English dictionary...")
        # Create a basic English alphabet dictionary as fallback
        with open(dict_file, 'w') as f:
            chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
            for char in chars:
                f.write(f"{char}\n")
        print(f"✓ Basic dictionary created at {dict_file}")

def main():
    # Create directories
    os.makedirs('downloads', exist_ok=True)
    os.makedirs('optimized_models', exist_ok=True)
    
    print("=" * 60)
    print(f"PP-OCR Model Optimization for Android ({OCR_VERSION.upper()})")
    print("=" * 60)
    
    # Check if paddle_lite_opt is installed
    if os.system("paddle_lite_opt --help > /dev/null 2>&1") != 0:
        print("\n✗ Error: paddle_lite_opt not found!")
        print("Please install it with: pip install paddlelite")
        sys.exit(1)
    
    # Download and optimize each model
    for model_type, config in MODELS.items():
        print(f"\n{'=' * 60}")
        print(f"Processing {model_type.upper()} model")
        print('=' * 60)
        
        # Download - check if it's a Hugging Face repo or tar archive
        is_hf = config.get('is_hf_repo', False)
        if is_hf:
            download_hf_model(config['url'], config['name'], 'downloads')
        else:
            download_and_extract(config['url'], 'downloads')
        
        # Optimize
        success = optimize_model('downloads', config['name'], config['output'])
        
        if not success:
            print(f"\n✗ Failed to process {model_type} model")
            sys.exit(1)
    
    # Download dictionary
    download_ppocr_dict()
    
    # Clean up downloads
    print("\nCleaning up temporary files...")
    shutil.rmtree('downloads')
    
    print("\n" + "=" * 60)
    print("✓ All models optimized successfully!")
    print("=" * 60)
    print(f"\nOptimized models are in: ./optimized_models/")
    print("\nNext steps:")
    print("1. Copy the .nb files to your Android project:")
    print("   app/src/main/assets/models/")
    print("2. Copy ppocr_keys_v1.txt to:")
    print("   app/src/main/assets/")
    print("\nModel files:")
    for file in os.listdir('optimized_models'):
        size = os.path.getsize(os.path.join('optimized_models', file)) / (1024 * 1024)
        print(f"  - {file} ({size:.2f} MB)")

if __name__ == "__main__":
    main()
