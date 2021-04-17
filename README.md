# 派图

## 简介

<img src="https://github.com/zzzzzzpc/portrait-segmentation/blob/master/app/src/main/res/drawable/portrait.png?raw=true" width="25%" height="25%" />

派图app采用了mediapipe框架，通过神经网络方法实现安卓平台实时的图像分割。

## 原理与实现

<img src="https://github.com/google/mediapipe/raw/master/docs/images/mediapipe_small.png" alt="M" style="zoom:50%;" />

Mediapipe借助手机Gpu加速以及核心计算器的模块化嵌入等内容实现了自定义功能强大且跨平台实时视频流的ML解决方案。

[Mediapipe on Github](https://github.com/google/mediapipe)

### 计算器（calculator）的自定义

在Mediapipe中，每一个计算器（calculator）都是图的一个节点（graph），使用C++书写，其计算器定义了整个流程的输入输出流，时间戳以及其他高级选项。计算器能够接受多个输入或者输出，也可以产生多个输入或者输出（也可以不产生输出）。

在人像分割部分，为了实现背景的替换，我在原有Mediapipe发色分割的图中，在最后MaskOverlay部分加入图像的输入，将背景替换，接受TFliteTensorToSegmentaion输出的视频流，这一部分采取了opencv的操作，将mask替换。

在发色替换的部分，加入了ColorSlider计算器，将RGB色彩值三个作为int数组输入，在将其作为输出输入至recolor计算器，在HairActivity中监听RGB数值的变化，实现发色的实时替换。

### Graph图

二进制graph图如下：（于[/app/src/main/assets](https://github.com/zzzzzzpc/portrait-segmentation/tree/master/app/src/main/assets)目录下）

hair_segmentation.binarypb

portrait_segmentation.binarypb

其中该目录下的back.jpg为替换的背景图像，这里需要注意的是，谨慎使用分辨率大的背景图像（例如4K），opencv带来的背景替换开销可能使得软件卡顿。

### Tflite文件

需要在calculator中指定对应的tflite文件：（于[/app/src/main/assets](https://github.com/zzzzzzpc/portrait-segmentation/tree/master/app/src/main/assets)目录下）

hair_segmentation.tflite

portrait_segmentation.tflite

这里已经提供了训练完成的tflite文件，如果需要完成你自己的高级功能，你可能需要训练自己的模型，不过谷歌提供的发色模型已经帮助了我们许多。

[这里](https://google.github.io/mediapipe/solutions/models.html#hair_segmentation)提供了许多谷歌训练完成的模型，方便使用。

### Paper

- [Real-time Hair Segmentation and Recoloring on Mobile GPUs](https://arxiv.org/abs/1907.06740)

