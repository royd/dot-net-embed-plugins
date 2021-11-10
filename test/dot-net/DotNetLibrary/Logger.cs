using System;
using Android.Runtime;

namespace Com.Roy.DotNetLibrary
{
    [Register("com.roy.dotnetlibrary.Logger")]
    public class Logger : Java.Lang.Object
    {
        [Java.Interop.Export("log")]
        public void Log(string message)
        {
            Console.WriteLine(message);
        }
    }
}
