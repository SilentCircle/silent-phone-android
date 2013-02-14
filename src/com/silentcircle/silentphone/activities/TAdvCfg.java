/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal 
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.silentcircle.silentphone.activities;
import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.utils.TZRTP;

import android.widget.Toast;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import android.view.View;

//import com.example.android.apis.R;

public class TAdvCfg extends PreferenceActivity {
   
	static PreferenceActivity _this;
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      _this=this;
      
      setPreferenceScreen(createPreferenceHierarchy());
   }
   @Override
   protected void onStop(){
      super.onStop();
      //TiviPhoneService.getSetCfgVal(2,byteResp,0,byteResp);//save
   }
   byte[] byteResp=new byte[128];
   boolean getTCValueCB(String s){
      //TiviPhoneService mc=TiviPhoneService.mc;
      //if(mc==null)return "a";
      if(TiviPhoneService.getSetCfgVal(1,s.getBytes(),s.length(),byteResp)<0){
         return false;
      }
      return byteResp[0]!=(byte)'0';
      
   }
   static boolean hasUserPWD(){
      byte[] resp=new byte[128];
      String s="edUN";
      TiviPhoneService.getSetCfgVal(1,s.getBytes(),s.length(),resp);
      boolean b=resp[0]!=(byte)'0';
      resp=null;
      return b; 
   }
   CBP createCB(String key, String name){
      CBP cb=new CBP(this);
      cb.setKey(key);
      cb.setTitle(name);
      boolean bChecked=getTCValueCB(key);
      cb.setTVal(bChecked);
      return cb;
   }
   String getTCValue(String s){
      //TiviPhoneService mc=TiviPhoneService.mc;
      //if(mc==null)return "a";
      if(TiviPhoneService.getSetCfgVal(1,s.getBytes(),s.length(),byteResp)<0){
         return "";
      }
      int l=0;
      while(byteResp[l]!=0 && l<64)l++;
      String ret=new String(byteResp,0,l);
      return ret;
      
   }
   static byte[] byteR=new byte[256];
   static String getTCValueS(String s ){
      //TiviPhoneService mc=TiviPhoneService.mc;
      //if(mc==null)return "a";
      if(TiviPhoneService.getSetCfgVal(1,s.getBytes(),s.length(),byteR)<0){
         return "";
      }
      int l=0;
      while(byteR[l]!=0 && l<64)l++;
      String ret=new String(byteR,0,l);
      return ret;
      
   }
   
   //    EditTextPreference createEB( String key, String name){
   ETP createEB( String key, String name){
      return     createEB(key,name,1);
   }
   //    EditTextPreference createEB( String key, String name, int iSetSum){
   ETP createEB( String key, String name, int iSetSum){
     // System.out.println(key+" "+name);
      ETP e = new ETP(this);
      e.key=key;
      e.setDialogTitle(name);
      e.setKey(key);
      e.setTitle(name);
      String s=getTCValue(key);
      // System.out.println("s="+s);
      if(iSetSum!=0)e.setSummary(s);
      e.setTVal(s);
      //  System.out.println("s1="+s);
      //	Editable e=editTextPref.getEditText().getText();//setText(s);
      //	e.clear();
      //     e.append(s);
      //EditText e=editTextPref.getEditText();e.setText(s);
      return e;
      
   }
   
   
   private PreferenceScreen createPreferenceHierarchy() {
      // Root
      PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
      
      // Inline preferences 
      
      // Toggle preference
      /*
       PreferenceCategory inlinePrefCat = new PreferenceCategory(this);
       inlinePrefCat.setTitle("inl");
       root.addPreference(inlinePrefCat);
       CheckBoxPreference togglePref = new CheckBoxPreference(this);
       togglePref.setKey("toggle_preference");
       togglePref.setTitle("CB");
       togglePref.setSummary("tp");
       inlinePrefCat.addPreference(togglePref);
       */
      // Dialog based preferences
      PreferenceCategory dialogBasedPrefCat = new PreferenceCategory(this);
      dialogBasedPrefCat.setTitle("User settings");
      root.addPreference(dialogBasedPrefCat);
      
      // Edit text preference
      //        EditTextPreference editTextPref = createEB("edUN","Username");//new EditTextPreference(this);
      ETP editTextPref = createEB("edUN","Username");//new EditTextPreference(this);
      dialogBasedPrefCat.addPreference(editTextPref);

      editTextPref = createEB("edNR","Number");//new EditTextPreference(this);
      dialogBasedPrefCat.addPreference(editTextPref);
      
      editTextPref = createEB("edPWD","Password",0);
      editTextPref.getEditText().setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
      dialogBasedPrefCat.addPreference(editTextPref);
      
      editTextPref = createEB("edGW","Server");
      dialogBasedPrefCat.addPreference(editTextPref);
      /*
       // List preference
       ListPreference listPref = new ListPreference(this);
       listPref.setEntries(R.array.entries_list_preference);
       listPref.setEntryValues(R.array.entryvalues_list_preference);
       listPref.setDialogTitle(R.string.dialog_title_list_preference);
       listPref.setKey("list_preference");
       listPref.setTitle(R.string.title_list_preference);
       listPref.setSummary(R.string.summary_list_preference);
       dialogBasedPrefCat.addPreference(listPref);
       */
      // Launch preferences
      
      
      // Screen preference
      
      
      // Launch preferences
      PreferenceCategory launchPrefCat = new PreferenceCategory(this);
      launchPrefCat.setTitle("Advanced");//R.string.launch_preferences);
      root.addPreference(launchPrefCat);
      
      // Screen preference
      PreferenceScreen screenPref = getPreferenceManager().createPreferenceScreen(this);
      screenPref.setKey("screen_preference");
      screenPref.setTitle("Advanced");
      launchPrefCat.addPreference(screenPref);
      /*
      PreferenceScreen ca = getPreferenceManager().createPreferenceScreen(this);
      ca.setKey("screen_preference2");
      ca.setTitle("Call options");
      launchPrefCat.addPreference(ca);
      
      CheckBoxPreference  cbx = new CheckBoxPreference(this);
      cbx.setKey("def_phone_is_tivi");
      cbx.setTitle("Dial out preference");
      cbx.setSummary("Use Tiviphone as main dialer");
      ca.addPreference(cbx);
      
      cbx = new CheckBoxPreference(this);
      cbx.setKey("use_prefix");
      cbx.setTitle("Set dial-out prefix");
      cbx.setSummary("if prefix is 1* then use TiviPhone. (2* for video call)");
      ca.addPreference(cbx);
      
      EditTextPreference edp=new EditTextPreference(this);
      edp.setKey("any_prefix");
      edp.setTitle("Check prefix");
      edp.setSummary("Enter any prefix");
      edp.setDialogTitle("Enter any prefix");
      ca.addPreference(edp);
      */
      
      
      
      /*
       * You can add more preferences to screenPref that will be shown on the
       * next screen.
       */
      
      // Example of next screen toggle preference
      /*
       CheckBoxPreference nextScreenCheckBoxPref = new CheckBoxPreference(this);
       nextScreenCheckBoxPref.setKey("VAD");
       nextScreenCheckBoxPref.setTitle("Use VAD");
       //        nextScreenCheckBoxPref.setSummary(R.string.summary_next_screen_toggle_preference);
       
       */
      //enable software ec
      CBP nextScreenCheckBoxPref;
      nextScreenCheckBoxPref=createCB("iUseVAD","Use VAD");
      screenPref.addPreference(nextScreenCheckBoxPref);
      
      editTextPref = createEB("szACodecs","Audio codec sequence");
      screenPref.addPreference(editTextPref);
      
      editTextPref = createEB("edREREG","Reregister time");
      screenPref.addPreference(editTextPref);
      //editTextPref.setSummary("Default: 3,0,8");
      
      editTextPref = createEB("iSipPort","Local SIP port");
      screenPref.addPreference(editTextPref);
      
      editTextPref = createEB("iRtpPort","Local RTP port");
      screenPref.addPreference(editTextPref);
      
      nextScreenCheckBoxPref=createCB("stun.iUse","Use Stun");
      screenPref.addPreference(nextScreenCheckBoxPref);
      
      nextScreenCheckBoxPref=createCB("iUseOnlyNatIp","Use only internal ip");
      screenPref.addPreference(nextScreenCheckBoxPref);
      
      editTextPref = createEB("edNatPX","SIP proxy");
      screenPref.addPreference(editTextPref);
      
      editTextPref = createEB("edSTUN","Stun IP:PORT");
      // editTextPref.setSummary("Default: stun.tiviphone.com");
      screenPref.addPreference(editTextPref);
      
      
      if(TZRTP.bEnabled){
         
         PreferenceScreen zrtp = getPreferenceManager().createPreferenceScreen(this);
         zrtp.setKey("screen_preference3");
         zrtp.setTitle("ZRTP encrytion");
         launchPrefCat.addPreference(zrtp);
         
         
         nextScreenCheckBoxPref=createCB("iCanUseZRTP","Enable ZRTP");
         nextScreenCheckBoxPref.bIsZRTP=true;
         zrtp.addPreference(nextScreenCheckBoxPref);
         
         editTextPref = createEB("LKEY","Licence key");
         zrtp.addPreference(editTextPref);
         editTextPref.bIsZRTP=true;
         editTextPref.cbpZrtp=nextScreenCheckBoxPref;
         nextScreenCheckBoxPref._setSummary();
      }
      //System.out.println("s3=");
      
      
      /*
       // Intent preference
       PreferenceScreen intentPref = getPreferenceManager().createPreferenceScreen(this);
       intentPref.setIntent(new Intent().setAction(Intent.ACTION_VIEW)
       .setData(Uri.parse("http://www.android.com")));
       intentPref.setTitle(R.string.title_intent_preference);
       intentPref.setSummary(R.string.summary_intent_preference);
       launchPrefCat.addPreference(intentPref);
       */
      // Preference attributes
      /*
       PreferenceCategory prefAttrsCat = new PreferenceCategory(this);
       prefAttrsCat.setTitle(R.string.preference_attributes);
       root.addPreference(prefAttrsCat);
       
       // Visual parent toggle preference
       CheckBoxPreference parentCheckBoxPref = new CheckBoxPreference(this);
       parentCheckBoxPref.setTitle(R.string.title_parent_preference);
       parentCheckBoxPref.setSummary(R.string.summary_parent_preference);
       prefAttrsCat.addPreference(parentCheckBoxPref);
       */
      /*
       // Visual child toggle preference
       // See res/values/attrs.xml for the <declare-styleable> that defines
       // TogglePrefAttrs.
       TypedArray a = obtainStyledAttributes(R.styleable.TogglePrefAttrs);
       CheckBoxPreference childCheckBoxPref = new CheckBoxPreference(this);
       childCheckBoxPref.setTitle(R.string.title_child_preference);
       childCheckBoxPref.setSummary(R.string.summary_child_preference);
       childCheckBoxPref.setLayoutResource(
       a.getResourceId(R.styleable.TogglePrefAttrs_android_preferenceLayoutChild,
       0));
       prefAttrsCat.addPreference(childCheckBoxPref);
       a.recycle();
       */
      return root;
   }
}
class CBP extends CheckBoxPreference{
	CBP(TAdvCfg c){
		super(c);
	}
	int iClick=1;
	boolean b_TChecked=false;
	boolean bIsZRTP=false;
	public void setTVal(boolean b){
		b_TChecked=b;
		iClick=0;
		setChecked(b_TChecked);
		iClick=1;
	}
	@Override protected void onSetInitialValue (boolean restoreValue, Object defaultValue){
		iClick=0;
		super.onSetInitialValue(false,b_TChecked);
		setChecked(b_TChecked);
		iClick=1;
	}
	void _setSummary(){
      if(bIsZRTP && b_TChecked){
         if(TiviPhoneService.mc!=null){
            String str=TiviPhoneService.mc.doX(".ck");
            TiviPhoneService.getSetCfgVal(1,getKey().getBytes(),getKey().length(),_z);
            //					setSummary(b_TChecked?"on":"off");
            if(_z[0]=='1') setSummary("Passive");
            else if(_z[0]=='2') setSummary("Economy");
            else if(_z[0]=='3') setSummary("Business");
            else setSummary("Off");
            /*
             ri->pStringList[1]="Passive";
             ri->pStringList[2]="Economy";
             ri->pStringList[3]="Business";					
             */
         }
         
      }
      else setSummary(b_TChecked?"on":"off");
	}
	static byte[] _z=new byte[4];
	@Override protected void onClick(){
		super.onClick();
		b_TChecked=isChecked();
		if(iClick==1){
         _z[1]=0;_z[0]=b_TChecked?(byte)'1':(byte)'0';
         TiviPhoneService.getSetCfgVal(0,getKey().getBytes(),getKey().length(),_z);
         _setSummary();
		}
	}
}

class ETP extends EditTextPreference{
	String key=null;
	CBP cbpZrtp=null;
	ETP(TAdvCfg c){
		super(c);
		bIsZRTP=false;
	}
	boolean bIsZRTP;
	@Override
	protected void onBindDialogView (View view){
		super.onBindDialogView(view);
		getEditText().setText(sv);
	}
	@Override
	protected void onSetInitialValue  (boolean restoreValue, Object defaultValue){
		
		if(key!=null){sv=TAdvCfg.getTCValueS(key);}
		super.onSetInitialValue ( false, sv);
		//editText.setText(sv);
		
		
	}
	
	String sv="";
	public void setTVal(String v){
		sv=v;
	}
	@Override
	protected void onDialogClosed(boolean positiveResult){
		super.onDialogClosed(positiveResult);
      if(!positiveResult)return;
      
      setTCValue(getKey(),getText());
      setSummary(getText());
      setTVal(getText());
		if(bIsZRTP){
			if(TiviPhoneService.mc!=null){
				String str=TiviPhoneService.mc.doX(".ck");
				if(cbpZrtp!=null)cbpZrtp._setSummary();
				Toast.makeText(TAdvCfg._this, str,Toast.LENGTH_LONG).show();
			}
			
		}
	}
	static byte[] _z=new byte[256];
	void setTCValue(String k, String s){
      //TiviPhoneService mc=TiviPhoneService.mc;
      //if(mc==null)return "a";
		//static byte[] b=new byte[256];
		//s.get
      int i;
      for(i=0;i<s.length() && i<255;i++){
         _z[i]=(byte)s.charAt(i);
      }
      _z[i]=0;
		
		TiviPhoneService.getSetCfgVal(0,k.getBytes(),k.length(),_z);
	}
	
};