package org.omg.DynamicAny.DynAnyFactoryPackage;


/**
* org/omg/DynamicAny/DynAnyFactoryPackage/InconsistentTypeCode.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from /HUDSON/workspace/8-2-build-linux-amd64/jdk8u192/11895/corba/src/share/classes/org/omg/DynamicAny/DynamicAny.idl
* Saturday, October 6, 2018 6:46:21 AM PDT
*/

public final class InconsistentTypeCode extends org.omg.CORBA.UserException
{

  public InconsistentTypeCode ()
  {
    super(InconsistentTypeCodeHelper.id());
  } // ctor


  public InconsistentTypeCode (String $reason)
  {
    super(InconsistentTypeCodeHelper.id() + "  " + $reason);
  } // ctor

} // class InconsistentTypeCode