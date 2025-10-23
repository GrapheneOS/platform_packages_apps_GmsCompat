#!/bin/bash

set -o errexit -o pipefail

[[ -n $KEY_DIR ]] || {
  echo expected KEY_DIR in the environment
  exit 1
}

[[ -n $OS_BUILD_NUMBER ]] || {
  echo expected OS_BUILD_NUMBER in the environment
  exit 1
}

export OUT_DIR=out_gmscompat_lib

source build/envsetup.sh
lunch sdk_phone64_x86_64-cur-user
m GmsCompatLib apksigner

OUT=GmsCompatLibSigned.apk

"$ANDROID_HOST_OUT"/bin/apksigner sign --min-sdk-version 36 \
    --in "$ANDROID_PRODUCT_OUT"/system/app/GmsCompatLib/GmsCompatLib.apk --out $OUT \
    --key "$KEY_DIR"/gmscompat_lib.pk8 --cert "$KEY_DIR"/gmscompat_lib.x509.pem

PROPS="$OUT.props.toml"
echo "requiredSystemFeatures = [\"grapheneos.version >= $OS_BUILD_NUMBER\"]" > $PROPS

echo "Written $OUT, $OUT.idsig and $PROPS"
