/*******************************************************************************
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/

package net.adoptopenjdk.stf.environment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.adoptopenjdk.stf.StfException;

/**
 * A utility which prints which platform of the current JVM
 * 
 * TODO: This code should
 *   1) Operate a higher level than supplying magic strings. eg, add methods such as 'isWindows()'
 *   2) Avoid final else options which assume a certain platform. Things should be explicit, with the else throwing an exception.
 */
public class PlatformFinder {
	public enum Platform {
		LINUX("linux"),
		WINDOWS("win"),
		AIX("aix"),
		ZOS("zos"),
		BSD("bsd"),
		OSX("osx"),
		SOLARIS("sunos");
		
		private String shortName;
		private Platform(String shortName) { this.shortName = shortName; }
		public String getShortName() { return this.shortName; }
	}; 

	
	private static PlatformFinder instance = null;

	private Platform platform;
    private String osShortName;
    private String osArch;
    private String osEndian;
    private String osArchType;

	 
	public static void main(String[] args) throws StfException {
		System.out.println(PlatformFinder.getPlatformAsString());
		
		PlatformFinder.forcePlatform("win_ppcle-32");
		System.out.println(PlatformFinder.getPlatformAsString());
		
		PlatformFinder.forcePlatform("win_x86-64");
		System.out.println(PlatformFinder.getPlatformAsString());
	}

	private static PlatformFinder getInstance() throws StfException {
		if (instance == null) {
			instance = new PlatformFinder(
							 PlatformFinder.calcOSShortName(), 
							 PlatformFinder.calcArchName(), 
							 PlatformFinder.calcEndian(),
							 PlatformFinder.calcArchType());
		}
		
		return instance;
	}


	public PlatformFinder(String osShortName, String osArch, String osEndian, String osArchType) throws StfException {
		// Decode the osShortName, and set the platform field
		for (Platform p : Platform.values()) { 
			if (p.getShortName().equals(osShortName)) {
				platform = p;
			}
		}
		if (platform == null) { 
			throw new StfException("Failed to identify platform for OS short name: '" + osShortName + "'");
		}

		// Validate the architecture value
		String osArchRegex = "390|x86|ppc|x86|arm|riscv|sparc|loongarch";
		if (!osArch.matches(osArchRegex)) {
			throw new StfException("Unknown architecture value: '" + osArch + "'. Expected one of '" + osArchRegex + "'");
		}

		// Validate the endian value
		String osEndianRegex = "le|";
		if (!osEndian.matches(osEndianRegex)) {
			throw new StfException("Unknown endian value: '" + osEndian + "'. Expected one of '" + osEndianRegex + "'");
		}

		// Validate the word size value
		String osWordSizeRegex = "31|32|64";
		if (!osArchType.matches(osWordSizeRegex)) {
			throw new StfException("Unknown word size value: '" + osArchType + "'. Expected one of '" + osWordSizeRegex + "'");
		}

		this.osShortName = osShortName;
		this.osArch      = osArch;
		this.osEndian    = osEndian;
		this.osArchType  = osArchType;
	}
	
	
	public static void forcePlatform(String platformDescription) throws StfException {
        String patternString = "(.*)_(.*?)([l][e])?-(.*)";

        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(platformDescription);

        if (!matcher.find() || matcher.groupCount() != 4) {
        	throw new StfException("Failed to parse platform description. '" + platformDescription + "' "
        			+ "Should be in the form '<os-name>_<architecture>-<wordsize>', "
        			+ "eg, linux_x86-64");
        }
        
        String shortName    = matcher.group(1);
        String architecture = matcher.group(2);
        String endian       = matcher.group(3);
        String wordsize     = matcher.group(4);
        
        if (endian == null) {
        	endian = "";
        }

		instance = new PlatformFinder(shortName, architecture, endian, wordsize);
	}

    public static String getPlatformAsString() throws StfException {
    	PlatformFinder pf = getInstance();
        return pf.osShortName + "_" + pf.osArch + pf.osEndian + "-" + pf.osArchType;     	     	 
    }
    
    public static String getPlatformSimple() throws StfException {
    	return calcOSShortName();
    }

	public static Platform getPlatform() throws StfException {
		return PlatformFinder.getInstance().platform;
	}

	public static String getArchName() throws StfException {
		return PlatformFinder.getInstance().osArch;
	}

	public static String getArchType() throws StfException {
		return PlatformFinder.getInstance().osArchType;
	}

	/**
	 * Removes a platform suffix from a string. eg, converts 'apps-root.win' to 'apps-root'
	 * 
	 * @param str is the string with an optional platform suffix
	 * @return a string containing the string without a platform suffix.
	 */
	public static String removePlatformSuffix(String str) {
		return str.replaceAll(".linux$|.win$|.aix$|.zos$|.osx$|.bsd$", "");
	}


    private static String calcOSShortName()
    {
        // get the osName and make it lowercase
        String osName = System.getProperty("os.name").toLowerCase();
        
        // set the shortname to the osName if the current system is Linux
        // or AIX this is all that is needed
        String osShortName = osName;
            	 
        // if we are on z/OS remove the slash
        if (osName.equals("z/os")) {
            osShortName = "zos";
        }
        
        // if we are on a Windows machine use win as the shortname
        if (osName.contains("win")) {
            osShortName = "win";
        }
        
        // if we are on a Mac use osx as the shortname
        if (osName.contains("mac")) {
            osShortName = "osx";
        }   

        // if we are on BSD use bsd as the shortname
        if (osName.contains("bsd")) {
            osShortName = "bsd";
        }
        
        // if we are on sunos use sunos as the shortname
        if (osName.contains("sunos")) {
            osShortName = "sunos";
        }
                
        return osShortName;
    }
    
    
    private static String calcArchName()
    {
        // get the architecture name (ppc, amd etc), os name (z/OS, 
        // linux, etc) and the architecture type (31, 32, 64) 
        String osArch = System.getProperty("os.arch");
        
        if (osArch.length() >= 4 && osArch.substring(0, 4).equals("s390")) {
        	// if the current system is a 390 machine use 390 as the osArch    	 
            osArch = "390";    		 
        } else if (osArch.equals("amd64")) {
        	// if the current system is AMD64 use x86 as the osArch
            osArch = "x86";    		 
        } else if(osArch.contains("ppc64")) {
        	// if the current system is PPC64 use ppc as the osArch
            osArch = "ppc";    		 
        } else if(osArch.contains("aarch64")) {
                // if the current system is aarch64 use arm as the osArch
            osArch = "arm";
        } else if(osArch.contains("loongarch64")) {
            // if the current system is loongarch64 use loongarch as the osArch
           osArch = "loongarch";
	} else if(osArch.contains("riscv")) {
            // The openj9 jdk sets os.arch to riscv, the bisheng jdk sets it to riscv64
            osArch = "riscv";
        } else if(osArch.contains("sparcv9")) {
            // if the current system is sparcv9 use sparc as the osArch
            osArch = "sparc";
        } else if (osArch.length() == 4
        	 && osArch.charAt(0) == 'i'
        	 && Character.isDigit(osArch.charAt(1))
        	 && osArch.substring(2).equals("86")) {
            // if the current system is i?86 where ? is a digit use x86
            osArch = "x86";
        } else if (osArch.equals("x86_64")) {
        	// if the current system is x86_64 (which is true for Mac) use x86 as the osArch
            osArch = "x86";    		 
        }        
        
        return osArch;
    }


    private static String calcArchType() {
        // get the architecture name and the architecture type (32, 64)
        String osArchType = System.getProperty("sun.arch.data.model");
        String osArch = System.getProperty("os.arch");
           
        // if no Sun classes, use IBM one instead
        if (osArchType == null) {
            osArchType = System.getProperty("com.ibm.vm.bitmode");
        }
        
        return osArchType;
    }
    

    private static String calcEndian()
    {
        String osEndian = System.getProperty("sun.cpu.endian");
        String osArch = System.getProperty("os.arch");
        
        if ((osEndian.equals("little")) && (osArch.contains("ppc64")))  {
            osEndian = "le";
        } else {
            osEndian = "";
        }
        
        return osEndian;
    }
    

    public static boolean isWindows() throws StfException {
        return getPlatform() == Platform.WINDOWS;
    }

    
	public static boolean isLinux() throws StfException {
		return getPlatform() == Platform.LINUX;
	} 
	
	
	public static boolean isAix() throws StfException {
		return getPlatform() == Platform.AIX;
	}
	
	public static boolean isZOS() throws StfException {
		return getPlatform() == Platform.ZOS;
	}
	
	public static boolean isOSX() throws StfException {
		return getPlatform() == Platform.OSX;
	}
	
	public static boolean isSolaris() throws StfException {
		return getPlatform() == Platform.SOLARIS;
	}
}
