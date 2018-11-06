# 7.x
(for Studo 7/Mule 4.x)

## 7.3.0.1
* Studio 7 support

## 7.2.3.1
* Initial release
* SOAP is not tested yet (only REST)

# 6.x
(for Studio 6/Mule 3.x)

## 6.5.0.6
* Same change as 6.5.0.4 but for REST
* Ensure REST allows changing the HTTP listener config name based on mojo properties

## 6.5.0.5
* Add an option to tweak whether app name is added to the listener path

## 6.5.0.4
* Remove timestamp from mule-deploy.properties for SOAP generate cases. Should make higher level testing easier

## 6.5.0.3
* Fix issue with REST generation classpath/dependencies
* Work properly with different RAML filenames
