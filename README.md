# SchedulAR

Instructions :

Requirements : Vuforia SDK must be downloaded

1. Copy Project into Vufuria SDK "Samples" folder

- Optional :

- These steps are necessary if you want to build your own database using your own keys. The project already includes everything that is neccessary to build the application

2. Generate Development Key from Vuforia's website and copy key to "SampleApplicationSession.java"
    "setInitParameters ( ... )"
3. Generate Databse using Vuforia's website. Select a Local Database for generation
4. Upload all Target Images files from "Resources/Vuforia" to Local Database. Give each uploaded image a meaningful name, such as the number of a classroom. For example, 8-345, 8-348, etc. If not, the image will be recognized but no information will be displayed because the names of the Image Targets are used to query data in the Databases

- End of Optional

6. Run project and scan any of the Target Images found in "Resources/Vuforia" to see results
