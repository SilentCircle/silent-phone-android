#!/bin/bash

SC_BUILD_TYPE="$1"
SC_BUILD_CREDENTIALS="$2"

if [[ "$SC_BUILD_TYPE" = "DEVELOP" ]];
then
   BUILD_CONFIGURATION="Debug"
   echo "*** building develop configuration"
else
   BUILD_CONFIGURATION="Release"
   echo "*** building release configuration"
fi

# A way to force an absolute path
BUILD_BASE=`pwd`

BUILD_APP_ARCHS="armv7 arm64"

BUILD_APP_NAME="SilentPhone"
BUILD_ARCHIVE_NAME="SilentPhone"
BUILD_ARCHIVE="$BUILD_BASE/$BUILD_ARCHIVE_NAME.xcarchive"

PROJECT_NAME=VoipPhone
PROJECT_NAME_ENT=EnterprisePhone

BUILD_IPA_DEV="$BUILD_BASE/$PROJECT_NAME.ipa"
BUILD_IPA_ENT="$BUILD_BASE/$PROJECT_NAME_ENT.ipa"

BUILD_APP_VOIPPHONE_DIR="apple/ios"



# This script assumes that is being run for the top of the tree
# Verify by checking for the .git directory
#
function validate_build_root()
{
  echo "BUILD_BASE = $BUILD_BASE"

  if [ ! -e .git ]
  then
    echo "Must build from toplevel of the working tree"
    exit 
  fi
}


function create_build_results()
{
  build_results=$BUILD_BASE"/results"

  mkdir -p $build_results
}


function failed()
{
    local error=${1:-Undefined error}
    echo "Failed: $error" >&2
    exit 1
}


function failed_architecture_wanted_vs_got()
{
    local wanted=$1
    local got=$2
    # echo rather than fail at present
    # failed "Archive wrong architectures, expected: $BUILD_APP_ARCHS, found: $archs_only"
    # echo "!!!!! -> Archive wrong architectures, expected: $BUILD_APP_ARCHS, found: $archs_only"
    echo " ------> Archive wrong architectures, expected: $wanted, found: $got"
}


function get_architecture_only()
{
  local got=$1
  local archs_only=`expr "$got" : '.*:\(.*\)'`
        archs_only="${archs_only%"${archs_only##*[![:space:]]}"}"
        archs_only="${archs_only#"${archs_only%%[![:space:]]*}"}"
  echo  $archs_only
}


function get_architecture_from_binary() 
{
  local binary=$1
  local archive_archs=`xcrun -sdk iphoneos lipo -info "$binary"`
  local archs=$(get_architecture_only "$archive_archs")
  echo  $archs
}


function test_architecture_wanted_vs_got()
{
  local wanted=$1
  local got=$2

  if [ "$wanted" != "$got" ];
  then
      failed_architecture_wanted_vs_got "$wanted" "$got"
  fi
}


function git_submodules()
{
  echo "Initalize git submodules"
  git submodule update --init --recursive
  git submodule status
}


function unlock_keychain() 
{
set +x

  if [ ! -f "$SC_BUILD_CREDENTIALS" ]
  then
      cat << EOF
  The build credential file "$SC_BUILD_CREDENTIALS" could not be found.
  SC_BUILD_CREDENTIALS is the path to a local script which:
   - unlocks the keychain for signing
   - defines signing identities for outputs
   - exports host_archive_signing_identity as in 
     host_archive_signing_identity="iPhone Developer: Captain Nemo (18661868XX)"
     the archive for the Apple store signing identity
   - exports host_ipa_signing_identity as in 
     host_ipa_signing_identity="iPhone Developer: Captain Nemo (18661868XX)"
     the developer kit signing identity
   - exports host_ent_signing_identity as in 
     host_ent_signing_identity="iPhone Developer: Captain Nemo (18661868XX)"
     the enterprise signing identity
   - defines provisioning profiles:
     - exports SP_arc_provisioning_profile_guid as in 
       SP_arch_provisioning_profile_guid="12345678-8123-4567-9812-123456789999"
       used to submit to the app store
     - exports SP_arc_provisioning_profile_file as in 
       SP_arch_provisioning_profile_file="${HOME}/Library/MobileDevice/Provisioning Profiles/"$SP_arch_provisioning_profile_guid".mobileprovision"
       full path to developer archive provisioning profile
     - exports SP_dev_provisioning_profile_guid as in 
       SP_dev_provisioning_profile_guid="12345678-8123-4567-9812-123456789012"# 
       used to build developer kits
     - exports SP_dev_provisioning_profile_file as in 
       SP_dev_provisioning_profile_file="${HOME}/Library/MobileDevice/Provisioning Profiles/"$SP_dev_provisioning_profile_guid".mobileprovision"
       full path to developer kit provisioning profile
     - exports SP_ent_provisioning_profile_guid as in 
       SP_provisioning_profile_guid="12345678-8123-4567-9812-123456789012"
       for the enterprise identity
     - exports SP_ent_provisioning_profile_file as in 
       SP_ent_provisioning_profile_file="${HOME}/Library/MobileDevice/Provisioning Profiles/"$SP_ent_provisioning_profile_guid".mobileprovision"
       full path to enterprise kit provisioning profile
   - defines enterprise kit ids like:
     - exports ENT_BUNDLE_ID=com.mycompany.enterprisephone
     - exports ENT_BUNDLE_DISPLAY_NAME="My Phone"
     - exports ENT_APP_ID=2APPLEID44.com.mycompany.enterprisephone
EOF
      exit
  fi
  ls -l  $SC_BUILD_CREDENTIALS
  source $SC_BUILD_CREDENTIALS
}


function validate_keychain()
{
  echo "Active keychains:"
  security list-keychains

  echo "List of all CodeSigning identities on keychain"
  security find-identity -p codesigning -v

  echo 
  echo "Validate requested archive codesiging identity: $host_archive_signing_identity"
  (security find-certificate -a -c "$host_archive_signing_identity" -Z | grep ^SHA-1) || failed "can not find requested archive signing identity in certificates"  

  echo 
  echo "Validate requested ipa codesigning identity: $host_ipa_signing_identity"
  (security find-certificate -a -c "$host_ipa_signing_identity" -Z | grep ^SHA-1) || failed "can not find requested ipa signing identity in certificates"  
}


function describe_environment()
{
  echo "Who I am"
  whoami
  echo 
  echo "Xcode version"
  xcodebuild -version
  echo 
  echo "Latest repository change"
  git log --format="%h %an %cd %s" -1
  echo 
}


function describe_project()
{
  echo "Available schemes"
  for xcodeproject in `find . -name $PROJECT_NAME.xcodeproj` ; do
      xcodebuild -list -project "$xcodeproject"
  done
}


function describe_sdks()
{
  echo "Available SDKs"
  xcodebuild -showsdks  
}


function build_prepare()
{
  echo "create libs dir"
  mkdir -p $BUILD_BASE/libs
}


function polarssl_build()
{
  local project_name="polarssl"
  local project="$project_name"".xcodeproj"
  local directory="support/polarssl/"
  local logfile="$BUILD_BASE""/xcodebuild_""$project_name"".log"

  pushd $directory  > /dev/null

  echo "describe $project_name"
  xcodebuild -list -project $project

  set -x
  xcodebuild -verbose                 \
             -project $project        \
             -sdk iphoneos            \
             -configuration $BUILD_CONFIGURATION clean build \
             ARCHS="$BUILD_APP_ARCHS" \
  >| $logfile

  if [ $? -ne 0 ]
  then
    set +x
    tail -n20 $logfile
    failed "$project"
  else
    set +x
  fi

  popd  > /dev/null
}


function polarssl_post()
{
  echo "Copy libspolarssl.a to libs/"
  cp   $BUILD_BASE/support/polarssl/build/$BUILD_CONFIGURATION-iphoneos/libpolar_ssl.a  $BUILD_BASE/libs/
}


function polarssl_verify()
{
  echo "Verify libspolarssl.a"
  local library="$BUILD_BASE/libs/libpolar_ssl.a"
  local archs_found=$(get_architecture_from_binary "$library")

  echo 
  echo "architectures supported: $archs_found"
  test_architecture_wanted_vs_got "$BUILD_APP_ARCHS" "$archs_found"
}


function werner_zrtp_prepare()
{
  pushd "$BUILD_BASE/support"

  # if missing, create link 
  if [ ! -e zrtp ]
  then
      ln -s zrtpcpp zrtp
  fi

  popd > /dev/null
}


function werner_zrtp_build()
{
  local project_name="werner_zrtp"
  local project="$project_name"".xcodeproj"
  local directory="support/werner_zrtp"
#  local extraHeaderPaths="$BUILD_BASE""/xcodebuild_""$project_name"".log"
  local extraHeaderPaths="../../support/zrtpcpp/cryptcommon"
#    HEADER_SEARCH_PATHS=$extraHeaderPaths \

  local logfile="$BUILD_BASE""/xcodebuild_""$project_name"".log"

  pushd $directory > /dev/null

  echo "describe $project_name"
  xcodebuild -list -project $project

  set -x
  xcodebuild -verbose                 \
             -project $project        \
             -sdk iphoneos            \
             -configuration $BUILD_CONFIGURATION clean build \
             ARCHS="$BUILD_APP_ARCHS" \
  >| $logfile

  if [ $? -ne 0 ]
  then
    set +x
    tail -n20 $logfile
    failed "$project"
  else
    set +x
  fi

  popd > /dev/null
}


function werner_zrtp_post()
{
  echo "Copy libwerner_zrtp.a to libs/"
  cp $BUILD_BASE/support/werner_zrtp/build/$BUILD_CONFIGURATION-iphoneos/libwerner_zrtp.a  $BUILD_BASE/libs
}


function werner_zrtp_verify()
{
  echo "Verify libwerner_zrtp.a"
  local library="$BUILD_BASE/libs/libwerner_zrtp.a"
  local archs_found=$(get_architecture_from_binary "$library")

  echo 
  echo "architectures supported: $archs_found"
  test_architecture_wanted_vs_got "$BUILD_APP_ARCHS" "$archs_found"
}


function build_app_VoipPhone()
{
  set +x
  # a reference for later searches
  touch time-mark

  local project="$PROJECT_NAME"".xcodeproj"
  local logfile="$BUILD_BASE""/xcodebuild_""$PROJECT_NAME"".log"

  pushd "$BUILD_APP_VOIPPHONE_DIR" > /dev/null

  local    hPath="$BUILD_BASE""/support/zrtpcpp/clients/tivi";
  hPath="$hPath ""$BUILD_BASE""/support/zrtpcpp";
  hPath="$hPath ""$BUILD_BASE""/support/zrtpcpp/zrtp";
  hPath="$hPath ""$BUILD_BASE""/support/polarssl/include";

  echo "Building with configuration: $BUILD_CONFIGURATION"

  set -x
  xcodebuild -verbose                       \
             -archivePath "$BUILD_ARCHIVE"  \
             -project $project              \
             -sdk iphoneos                  \
             -scheme VoipPhone              \
             -configuration $BUILD_CONFIGURATION clean build archive \
     HEADER_SEARCH_PATHS="$hPath"                              \
     LIBRARY_SEARCH_PATHS="$BUILD_BASE/libs"                   \
     PROVISIONING_PROFILE="$SP_arc_provisioning_profile_guid"  \
     CODE_SIGN_IDENTITY="$host_archive_signing_identity"       \
     ARCHS="$BUILD_APP_ARCHS"                                  \
  >| $logfile

  if [ $? -ne 0 ]
  then
    set +x
    tail -n20 xcodebuild_output
    failed xcodebuild
  else
    set +x
  fi

  local project_plist="$PROJECT_NAME/$PROJECT_NAME-Info.plist"
  TEST_BUNDLE_VERSION=`/usr/libexec/PlistBuddy -c "Print :CFBundleVersion" $project_plist`

  popd > /dev/null
}



function archive_verify()
{
  set +x
  # the archive is built locally so there is no possibility this is not the archive just built
  # however leave this check in place for a while just to make sure
  # verify the archive has the BUILD_VERSION_ID of the app just built 
  
  local archivePlist="$BUILD_ARCHIVE/Info.plist"

  local bundleVersion=`/usr/libexec/PlistBuddy -c "Print :ApplicationProperties:CFBundleVersion" $archivePlist`

  if [ "$TEST_BUNDLE_VERSION" != "$bundleVersion" ];
  then
     failed "verify_archive wanted $TEST_BUNDLE_VERSION found $bundleVersion"
  fi
  echo "Archive bundle version: $bundleVersion"


  # list the hardware architectures supported by the xcarchive and verify
  # it matches what was expected
  #
  local archive_app="$BUILD_ARCHIVE/Products/Applications/$PROJECT_NAME.app"
  local archs_found=$(get_architecture_from_binary "$archive_app/$PROJECT_NAME")

  echo 
  echo "architectures supported: $archs_found"
  test_architecture_wanted_vs_got "$BUILD_APP_ARCHS" "$archs_found"

  echo 
  echo "Entitlements:"
  codesign -d --entitlements :- "$archive_app"

  echo 
  echo "Verify App in Archive:"
  codesign -d -vvv --file-list - "$archive_app" || failed verification 
}


function from_xcarchive_to_adhoc_ipa()
{
  local prov_profile_w_name_key="SilentPhone ad-hoc"
  local ipa_output_file="$BUILD_IPA_DEV"
  local logfile="$BUILD_BASE/from_xcarchive_to_developer_ipa.log"

  echo "Export \"$ipa_output_file\", embedding provisioning profile \"$provisioning_profile\""

  set -x
  xcodebuild -verbose                                            \
             -exportArchive                                      \
             -exportFormat ipa                                   \
             -archivePath "$BUILD_ARCHIVE"                       \
             -exportPath "$ipa_output_file"                      \
             -exportProvisioningProfile "$prov_profile_w_name_key" \
  >| $logfile

  if [ $? -ne 0 ]
  then
    set +x
    tail -n20 $logfile
    failed "xcodebuild_export"
  else
    tail -n3 $logfile
    set +x
  fi
}


function verify_app()
{
  codesign -d -vvv --file-list - "$BUILD_BASE/$project_app" || failed verification  
}


function verify_ipa()
{
  local ipa=$1
  unzip -q "$ipa"

  echo 
  echo "Entitlements:"
  codesign -d --entitlements :- "Payload/$PROJECT_NAME.app"

  echo 
  echo "Verify App in ipa:"
  codesign -d -vvv --file-list - "Payload/$PROJECT_NAME.app" || failed verification  

  rm -rf Payload
}


function from_developer_to_enterprise_ipa() {
  local PlistBuddy=/usr/libexec/PlistBuddy

  local signing_identity=$host_ent_signing_identity
  local enterprise_provisioning_profile=$SP_ent_provisioning_profile_file

  cat <<EOF
  codesign as:   $signing_identity
  using profile: $enterprise_provisioning_profile
  producing:     $BUILD_IPA_ENT

EOF

  #verify the resulting app
  unzip -q "$BUILD_IPA_DEV"

  rm -rf Payload/$PROJECT_NAME.app/_CodeSignature/

  cp "$enterprise_provisioning_profile" Payload/$PROJECT_NAME.app/embedded.mobileprovision

  # extract the entitlements from the provisioning profile
  security cms -D -i Payload/$PROJECT_NAME.app/embedded.mobileprovision > temp_profile.plist
  plutil -extract Entitlements xml1 -o temp_entitlements.plist temp_profile.plist

  # change the bundle identifier
  $PlistBuddy -c "Set :CFBundleIdentifier $ENT_BUNDLE_ID" Payload/VoipPhone.app/Info.plist

  # set name of app as appears on desktop
  $PlistBuddy -c "Set :CFBundleDisplayName $ENT_BUNDLE_DISPLAY_NAME" Payload/VoipPhone.app/Info.plist

  # resign app with correct identity, modified entitlements and resource rules
  codesign -f -s "$signing_identity" \
           --entitlements=temp_entitlements.plist  \
           --resource-rules Payload/$PROJECT_NAME.app/ResourceRules.plist \
           Payload/$PROJECT_NAME.app

  # create ipa
  zip -qr "$BUILD_IPA_ENT" Payload/

  # cleanup
  rm -rf Payload
  rm temp_entitlements.plist
  rm temp_profile.plist
} 



echo "**** Validate Build Root"
validate_build_root
echo
echo "**** set local values & Unlock Keychain"
unlock_keychain
echo
echo "**** Validate Keychain"
validate_keychain
echo
echo "**** Describe Environment"
describe_environment
echo
echo "**** Describe SDKs"
describe_sdks
echo
echo "**** Prepare for build"
build_prepare
echo
echo "**** Initialize git submodules"
git_submodules
echo
echo "**** Build submodules"
echo "**** Build polar_ssl"
polarssl_build
polarssl_post
polarssl_verify
echo
echo "**** Build werner_zrtp"
werner_zrtp_prepare
werner_zrtp_build
werner_zrtp_post
werner_zrtp_verify
echo
echo "**** Describe Project"
describe_project
echo
echo "**** Build Silent Phone app"
build_app_VoipPhone
echo
echo "**** Verify archive"
archive_verify
echo
echo "**** Re-package archive as development/ad-hoc ipa"
from_xcarchive_to_adhoc_ipa
echo
echo "**** Verify development/ad-hoc ipa"
verify_ipa "$BUILD_IPA_DEV"
echo
echo "**** Re-package development ipa as enterprise ipa"
from_developer_to_enterprise_ipa
echo
echo "**** Verify enterprise ipa"
verify_ipa "$BUILD_IPA_ENT"
echo
echo "**** Complete!"