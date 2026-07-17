#!/bin/bash
set -e

GROUP_PATH="io/github/phyapp/otalib/2.3.9"
M2_REPO="$HOME/.m2/repository"
STAGING_DIR="/tmp/otalib-central-staging"
ZIP_FILE="/tmp/otalib-2.3.9.zip"

echo "=== Step 1: Build and sign ==="
./gradlew :otalib:publishReleasePublicationToMavenLocal

echo ""
echo "=== Step 2: Package Maven artifacts ==="
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR/$GROUP_PATH"
cp "$M2_REPO/$GROUP_PATH/"* "$STAGING_DIR/$GROUP_PATH/"

echo "=== Generate MD5 and SHA1 checksums ==="
cd "$STAGING_DIR/$GROUP_PATH"
for f in *.jar *.aar *.pom *.module; do
    [ -f "$f" ] || continue
    # Strip any existing checksum files before regenerating
    md5 -q "$f" > "$f.md5"
    shasum "$f" | awk '{print $1}' > "$f.sha1"
done
cd - > /dev/null

cd "$STAGING_DIR"
zip -r "$ZIP_FILE" .
cd - > /dev/null
echo "ZIP created: $ZIP_FILE ($(wc -c < $ZIP_FILE) bytes)"

echo ""
echo "=== Step 3: Upload to Central Portal ==="
TOKEN_USER=fpxT37
TOKEN_PASS=$(grep 'centralPortalPassword' ~/.gradle/gradle.properties | cut -d= -f2)
AUTH=$(echo -n "${TOKEN_USER}:${TOKEN_PASS}" | base64)

echo "Uploading..."
HTTP_BODY=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "Authorization: Bearer ${AUTH}" \
  -F "bundle=@${ZIP_FILE}" \
  "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC")

HTTP_CODE=$(echo "$HTTP_BODY" | tail -1)
BODY=$(echo "$HTTP_BODY" | sed '$d')

echo "HTTP Code: $HTTP_CODE"
echo "Response: $BODY"

if [ "$HTTP_CODE" = "201" ]; then
    echo ""
    echo "=== 发布成功！==="
    echo "Deployment ID: $BODY"
    echo ""
    echo "等待几分钟后，可在以下地址验证："
    echo "https://central.sonatype.com/namespace/io.github.phyapp"
    echo ""
    echo "外部用户使用方式："
    echo "implementation 'io.github.phyapp:otalib:2.3.9'"
else
    echo ""
    echo "=== 发布失败 ==="
    exit 1
fi
