# Lightstreamer - Portfolio Demo - Java Adapter
<!-- START DESCRIPTION lightstreamer-example-portfolio-adapter-java -->
The *Portfolio Demo* simulates a portfolio management: it shows a list of stocks included in a portfolio and provide a simple order entry form. Changes to portfolio contents, as a result of new orders, are displayed on the page in real-time. In addition to that, the *Full Version of the Portfolio Demo* also shows, for each stock in the portfolio, the current price, updated in real-time from a market data feed.

This project shows the Java Metadata and Data Adapters for the *Portfolio Demo* and how they can be plugged into Lightstreamer Server. It also shows how to integrate the [Lightstreamer - Stock-List Demo - Java Adapter](https://github.com/Lightstreamer/Lightstreamer-example-StockList-adapter-java) into the same Adapter Set to support the full version of the *Portfolio Demo*.   

As an example of [Clients Using This Adapter](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-adapter-java#clients-using-this-adapter), you may refer to the [Basic Portfolio Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-client-javascript#basic-portfolio-demo---html-client) and view the corresponding [Basic Portfolio Demo Live Demo](http://demos.lightstreamer.com/PortfolioDemo_Basic/), or you may refer to the [Portfolio Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-client-javascript#portfolio-demo---html-client) and view the corresponding [Portfolio Demo Live Demo](http://demos.lightstreamer.com/PortfolioDemo/) for the full version of the *Portfolio Demo*.

## Details

### Dig the Code
The project is comprised of source code and a deployment example. The source code is divided into three folders.

#### Feed Simulator 
Contains the source code for a class that simulates a portfolio manager, which generates random portfolios and accepts buy and sell operations to change portfolio contents.

#### Portfolio DataAdapter
Contains the source code for the Basic Portfolio Demo Data Adapter, a demo Adapter that handles subscription requests by attaching to the simulated portfolio manager.
It can be referred to as a basic example for Data Adapter development.

#### Portfolio MetaDataAdapter
Contains the source code for a Metadata Adapter to be associated with the Portfolio Demo Data Adapter. This Metadata Adapter inherits from `LiteralBasedProvider` in [Lightstreamer Java In-Process Adapter SDK](https://github.com/Lightstreamer/Lightstreamer-lib-adapter-java-inprocess#literalbasedprovider-metadata-adapter) and just adds a simple support for order entry by implementing the NotifyUserMessage method, to handle "sendMessage" requests from the Portfolio Demo client.
The communication to the Portfolio Feed Simulator, through the Portfolio Data Adapter, is handled here.

It should not be used as a reference for a real case of client-originated message handling, as no guaranteed delivery and no clustering support is shown.

See the source code comments for further details.
<!-- END DESCRIPTION lightstreamer-example-portfolio-adapter-java -->

#### The Adapter Set Configuration

This Adapter Set is configured and will be referenced by the clients as `PORTFOLIODEMO`. 

The `adapters.xml` file for the Basic Portfolio Demo, should look like:
```xml
<?xml version="1.0"?>

<!-- Mandatory. Define an Adapter Set and sets its unique ID. -->
<adapters_conf id="PORTFOLIODEMO">

    <metadata_adapter_initialised_first>Y</metadata_adapter_initialised_first>

    <!-- Mandatory. Define the Metadata Adapter. -->
    <metadata_provider>

        <!-- Mandatory. Java class name of the adapter. -->
        <adapter_class>portfolio_demo.adapters.PortfolioMetadataAdapter</adapter_class>

        <!-- Optional, managed by the inherited LiteralBasedProvider.
             See LiteralBasedProvider javadoc. -->
        <param name="item_family_1">portfolio.*</param>
        <param name="modes_for_item_family_1">COMMAND</param>

    </metadata_provider>

    <!-- Mandatory. Define the Data Adapter. -->
    <data_provider name="PORTFOLIO_ADAPTER">

        <!-- Mandatory. Java class name of the adapter. -->
        <adapter_class>portfolio_demo.adapters.PortfolioDataAdapter</adapter_class>

    </data_provider>

</adapters_conf>
```

The *full version of the Portfolio Demo* needs the Portfolio and the StockList demo adapters together in the same Adapter Set. 

The `adapters.xml` file for the Portfolio Demo, should look like:
```xml 
<?xml version="1.0"?>

<!-- Mandatory. Define an Adapter Set and sets its unique ID. -->
  <adapters_conf id="FULLPORTFOLIODEMO">
  
    <metadata_adapter_initialised_first>Y</metadata_adapter_initialised_first>

    <!-- Mandatory. Define the Metadata Adapter. -->
    <metadata_provider>

      <install_dir>portfolio</install_dir>

      <!-- Mandatory. Java class name of the adapter. -->
      <adapter_class>portfolio_demo.adapters.PortfolioMetadataAdapter</adapter_class>

      <!-- Optional, managed by the inherited LiteralBasedProvider.
           See LiteralBasedProvider javadoc. -->
      <param name="item_family_1">portfolio.*</param>
      <param name="modes_for_item_family_1">COMMAND</param>

      <param name="item_family_2">item.*</param>
      <param name="modes_for_item_family_2">MERGE</param>

    </metadata_provider>

    <!-- Mandatory. Define the Data Adapter. -->
    <data_provider name="PORTFOLIO_ADAPTER">

      <install_dir>portfolio</install_dir>

      <!-- Mandatory. Java class name of the adapter. -->
      <adapter_class>portfolio_demo.adapters.PortfolioDataAdapter</adapter_class>

    </data_provider>

    <!-- Mandatory. Define the Data Adapter. -->
    <data_provider name="QUOTE_ADAPTER">

      <install_dir>Stocklist</install_dir>

      <!-- Mandatory. Java class name of the adapter. -->
      <adapter_class>stocklist_demo.adapters.StockQuotesDataAdapter</adapter_class>

    </data_provider>

  </adapters_conf>
```


<i>NOTE: not all configuration options of an Adapter Set are exposed by the files suggested above. 
You can easily expand your configurations using the generic template, see the [Java In-Process Adapter Interface Project](https://github.com/Lightstreamer/Lightstreamer-lib-adapter-java-inprocess#configuration) for details.</i><br>
<br>
Please refer [here](https://lightstreamer.com/docs/ls-server/latest/General%20Concepts.pdf) for more details about Lightstreamer Adapters.

## Install

### Install the Basic Portfolio Demo
If you want to install a basic version of the *Portfolio Demo* in your local Lightstreamer Server, follow these steps:
* Download *Lightstreamer Server* (Lightstreamer Server comes with a free non-expiring demo license for 20 connected users) from [Lightstreamer Download page](http://www.lightstreamer.com/download.htm), and install it, as explained in the `GETTING_STARTED.TXT` file in the installation home directory.
* Make sure that Lightstreamer Server is not running.
* In the `adapters` folder of your Lightstreamer Server installation, you may find a `Demo` folder, containing some adapters ready-made for several demo including the Portfolio one. If this is the case, you already have a Portfolio Demo Adapter installed and you may stop here. Please note that, in case of Demo folder already installed, the MetaData Adapter jar installed is a mixed one that combines the functionality of several demos. If the Demo folder is not installed, or you have removed it, or you want to install the Portfolio Adapter Set alone, please continue to follow the next steps.
* Get the `deploy.zip` file of the [proper release](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-adapter-java/releases), unzip it, go to the `Deployment_LS` folder, and copy the `Portfolio` folder into the `adapters` folder of your Lightstreamer Server installation.
* Launch Lightstreamer Server.
* Test the Adapter, launching the [Basic Portfolio Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-client-javascript#basic-portfolio-demo---html-client), listed in [Clients Using This Adapter](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-adapter-java#clients-using-this-adapter).

### Install the Portfolio Demo
To work with full functionality, the [Portfolio Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-client-javascript#portfolio-demo---html-client), needs both the *PORTFOLIO_ADAPTER*, from the *Portfolio Demo*, and the *QUOTE_ADAPTER*, from the *Stock-List Demo* (see [Lightstreamer - Stock-List Demo - Java Adapter](https://github.com/Lightstreamer/Lightstreamer-example-StockList-adapter-java)). 
If you want to install a full version of the *Portfolio Demo* in your local Lightstreamer Server, you have to deploy the *PORTFOLIO_ADAPTER* and the *QUOTE_ADAPTER* together in the same Adapter Set. 
To allow the two adapters to coexist within the same Adapter Set, please follow the steps below:
* Download *Lightstreamer Server* (Lightstreamer Server comes with a free non-expiring demo license for 20 connected users) from [Lightstreamer Download page](http://www.lightstreamer.com/download.htm), and install it, as explained in the `GETTING_STARTED.TXT` file in the installation home directory.
* Make sure that Lightstreamer Server is not running.
* In the `adapters` folder of your Lightstreamer Server installation, you may find a `Demo` folder, containing some adapters ready-made for several demo including the Portfolio one. If this is the case, you already have a Portfolio Demo Adapter installed and you may stop here. Please note that, in case of Demo folder already installed, the MetaData Adapter jar installed is a mixed one that combines the functionality of several demos. If the Demo folder is not installed, or you have removed it, or you want to install the Portfolio Adapter Set alone, please continue to follow the next steps.
* Get the `deploy.zip` file of the [proper release](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-adapter-java/releases), unzip it, go to the `Full_Deployment_LS` folder, and copy the `FullPortfolio` folder into the `adapters` folder of your Lightstreamer Server installation.
* Launch Lightstreamer Server.
* Test the Adapter, launching the [Portfolio Demo - HTML Client](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-client-javascript#portfolio-demo---html-client), listed in [Clients Using This Adapter](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-adapter-java#clients-using-this-adapter).

## Build


To build your own version of `example-Portfolio-adapter-java-0.0.1-SNAPSHOT.jar` instead of using the one provided in the `deploy.zip` file from the [Install](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-adapter-java#install) section above, you have two options:
either use [Maven](https://maven.apache.org/) (or other build tools) to take care of dependencies and building (recommended) or gather the necessary jars yourself and build it manually.
For the sake of simplicity only the Maven case is detailed here.

### Maven

You can easily build and run this application using Maven through the pom.xml file located in the root folder of this project. As an alternative, you can use an alternative build tool (e.g. Gradle, Ivy, etc.) by converting the provided pom.xml file.

Assuming Maven is installed and available in your path you can build the demo by running
```sh 
 mvn install dependency:copy-dependencies 
```

## See Also

### Clients Using This Adapter
<!-- START RELATED_ENTRIES -->

* [Lightstreamer - Portfolio Demos - HTML Clients](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-client-javascript#portfolio-demo)
* [Lightstreamer - Portfolio Demo - .NET Client](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-client-dotnet)
* [Lightstreamer - Portfolio Demo - Flex Client](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-client-flex)
* [Lightstreamer - Portfolio Demo - Dojo Toolkit Client](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-client-dojo)

<!-- END RELATED_ENTRIES -->

### Related Projects

* [LiteralBasedProvider Metadata Adapter](https://github.com/Lightstreamer/Lightstreamer-lib-adapter-java-inprocess#literalbasedprovider-metadata-adapter)
* [Lightstreamer - Stock-List Demo - Java Adapter](https://github.com/Lightstreamer/Lightstreamer-example-StockList-adapter-java)
* [Lightstreamer - Portfolio Demo - .NET Adapter](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-adapter-dotnet)

## Lightstreamer Compatibility Notes

- Compatible with Lightstreamer SDK for Java In-Process Adapters since 7.3.
- For a version of this example compatible with Lightstreamer SDK for Java In-Process Adapters version 6.0, please refer to [this tag](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-adapter-java/tree/pre_mvn).
- For a version of this example compatible with Lightstreamer SDK for Java In-Process Adapters version 5.1, please refer to [this tag](https://github.com/Lightstreamer/Lightstreamer-example-Portfolio-adapter-java/releases/tag/for_Lightstreamer_5.1.2).
