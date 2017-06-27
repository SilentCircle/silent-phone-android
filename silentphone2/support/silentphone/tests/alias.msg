INVITE tel:+2345 SIP/2.0
Via: SIP/2.0/TCP client.atlanta.example.com:5060;branch=z9hG4bK74bf9
Max-Forwards: 70
From: Alice <tel:1234>;tag=9fxced76sl
To: Bob <mailto:bob@biloxi.example.com>
Call-ID: 3848276298220188511@atlanta.example.com
CSeq: 1 INVITE
Contact: <sip:alice@client.atlanta.example.com;transport=tcp>
P-Associated-URI: <mailto:alice@corp.example.com>;type=enterprise,<sip:1@aa.com>,<tel:+12312>
X-SC-Display-Alias: john.galt@example.com;type=email
Content-Type: application/sdp
Content-Length: 144

v=0
o=alice 2890844526 2890844526 IN IP4 client.atlanta.example.com
s=-
c=IN IP4 192.0.2.101
t=0 0
m=audio 49172 RTP/AVP 0
a=rtpmap:0 PCMU/8000

