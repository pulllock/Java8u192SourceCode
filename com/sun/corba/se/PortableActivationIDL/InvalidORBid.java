package com.sun.corba.se.PortableActivationIDL;


/**
* com/sun/corba/se/PortableActivationIDL/InvalidORBid.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from /HUDSON/workspace/8-2-build-linux-amd64/jdk8u192/11895/corba/src/share/classes/com/sun/corba/se/PortableActivationIDL/activation.idl
* Saturday, October 6, 2018 6:46:21 AM PDT
*/

public final class InvalidORBid extends org.omg.CORBA.UserException
{

  public InvalidORBid ()
  {
    super(InvalidORBidHelper.id());
  } // ctor


  public InvalidORBid (String $reason)
  {
    super(InvalidORBidHelper.id() + "  " + $reason);
  } // ctor

} // class InvalidORBid
