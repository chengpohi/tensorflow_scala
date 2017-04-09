package org.platanios.tensorflow.api.ops

import org.platanios.tensorflow.api.{DataType, Shape, Tensor, using}

import scala.util.DynamicVariable

/**
  * @author Emmanouil Antonios Platanios
  */
object ArrayOps {
  def constant(value: Any, dataType: DataType = null, name: String = "Constant")
      (implicit context: DynamicVariable[OpCreationContext]): Op.Output = {
    using(Tensor.create(value = value)) { tensor =>
      val opBuilder = Op.Builder(context = context, opType = "Const", name = name)
      opBuilder.setAttribute(name = "value", value = tensor)
      if (dataType != null)
        opBuilder.setAttribute(name = "dtype", value = dataType)
      else
        opBuilder.setAttribute(name = "dtype", value = tensor.dataType)
      opBuilder.build().outputs(0)
    }
  }

  /** Creates a placeholder op for a tensor that will always be fed.
    *
    * IMPORTANT NOTE: This op will produce an error if evaluated. Its value must be fed when using `Session.run`. It is
    * intended as a way to represent a value that will always be fed, and to provide attributes that enable the fed
    * value to be checked at runtime.
    *
    * @param  dataType Data type of the elements in the tensor that will be fed.
    * @param  shape    Shape of the tensor that will be fed. The shape can be any partially-specified, or even
    *                  completely unknown.
    * @param  name     Name for the created op.
    * @param  context  Op creation context.
    * @return Created op.
    */
  def placeholder(dataType: DataType, shape: Shape = null, name: String = "Placeholder")
      (implicit context: DynamicVariable[OpCreationContext]): Op.Output = {
    if (shape != null) {
      Op.Builder(context = context, opType = "PlaceholderV2", name = name)
          .setAttribute(name = "dtype", value = dataType)
          .setAttribute(name = "shape", value = shape)
          .build().outputs(0)
    } else {
      Op.Builder(context = context, opType = "Placeholder", name = name)
          .setAttribute(name = "dtype", value = dataType)
          .build().outputs(0)
    }
  }

  /** Creates a placeholder op that passes through `defaultValue` when its input is not fed.
    *
    * @param  defaultValue Default value to pass through when no input is fed for this placeholder.
    * @param  shape        Shape of the tensor that will be fed. The shape can be any partially-specified, or even
    *                      completely unknown.
    * @param  name         Name for the created op.
    * @param  context      Op creation context.
    * @return Created op.
    */
  def placeholderWithDefault(defaultValue: Any, shape: Shape, name: String = "PlaceholderWithDefault")
      (implicit context: DynamicVariable[OpCreationContext]): Op.Output = {
    val default: Op.Output = constant(value = defaultValue, name = s"$name/DefaultValue")
    Op.Builder(context = context, opType = "PlaceholderWithDefault", name = name)
        .addInput(default)
        .setAttribute(name = "shape", value = shape)
        .build().outputs(0)
  }

  /** Creates an op that inserts a dimension of size 1 into a tensor's shape.
    *
    * Given an op output `input`, this op inserts a dimension of size 1 at the dimension index `axis` of `input`'s
    * shape. The dimension index `axis` starts at zero; if you specify a negative number for `axis` it is counted
    * backwards from the end.
    *
    * This op is useful if you want to add a batch dimension to a single element. For example, if you have a single
    * image of shape `[height, width, channels]`, you can make it a batch of 1 image with `expandDims(image, 0)`, which
    * will make the shape equal to `[1, height, width, channels]`.
    *
    * For example:
    * {{{
    *   // 't1' is an op output with shape [2]
    *   shape(expandDims(t1, 0)) == [1, 2]
    *   shape(expandDims(t1, 1)) == [2, 1]
    *   shape(expandDims(t1, -1)) == [2, 1]
    *
    *   // 't2' is a tensor of shape [2, 3, 5]
    *   shape(expandDims(t2, 0)) == [1, 2, 3, 5]
    *   shape(expandDims(t2, 2)) == [2, 3, 1, 5]
    *   shape(expandDims(t2, 3)) == [2, 3, 5, 1]
    * }}}
    *
    * This op requires that `-1 - input.shape.rank <= axis <= input.shape.rank`.
    *
    * This op is related to [[squeeze]], which removes dimensions of size 1.
    *
    * @param  input   Input tensor.
    * @param  axis    Dimension index at which to expand the shape of `input`.
    * @param  name    Name for the created op.
    * @param  context Op creation context.
    * @return Created op.
    */
  def expandDims(input: Op.Output, axis: Long, name: String = "ExpandDims")
      (implicit context: DynamicVariable[OpCreationContext]): Op.Output = {
    val axisConstant: Op.Output = constant(value = axis, name = s"$name/Axis")
    Op.Builder(context = context, opType = "ExpandDims", name = name)
        .addInput(input)
        .addInput(axisConstant)
        .build().outputs(0)
  }

  /** Creates an op that removes dimensions of size 1 from the shape of a tensor.
    *
    * Given a tensor `input`, this op returns a tensor of the same data type, with all dimensions of size 1 removed. If
    * `axes` is specified, then only the dimensions specified by that array will be removed. In that case, all these
    * dimensions need to have size 1.
    *
    * For example:
    * {{{
    *   // 't' is a tensor of shape [1, 2, 1, 3, 1, 1]
    *   shape(squeeze(t)) == [2, 3]
    *   shape(squeeze(t, Array(2L, 4L))) ==> [1, 2, 3, 1]
    * }}}
    *
    * @param  input   Input tensor.
    * @param  axes    Dimensions of size 1 to squeeze. If this argument is not provided, then all dimensions of size 1
    *                 will be squeezed.
    * @param  name    Name for the created op.
    * @param  context Op creation context.
    * @return Created op.
    */
  def squeeze(input: Op.Output, axes: Array[Long] = null, name: String = "Squeeze")
      (implicit context: DynamicVariable[OpCreationContext]): Op.Output = {
    val builder = Op.Builder(context = context, opType = "Squeeze", name = name)
        .addInput(input)
    if (axes != null)
      builder.setAttribute("squeeze_dims", axes)
    builder.build().outputs(0)
  }

  /** Creates an op that stacks a list of rank-`R` tensors into one rank-`(R+1)` tensor.
    *
    * The op packs the list of tensors in `values` into a tensor with rank one higher than each tensor in `values`, by
    * packing them along the `axis` dimension. Given a list of `N` tensors of shape `[A, B, C]`:
    *   - If `axis == 0`, then the output tensor will have shape `[N, A, B, C]`.
    *   - If `axis == 1`, then the output tensor will have shape `[A, N, B, C]`.
    *   - If `axis == -1`, then the output tensor will have shape `[A, B, C, N]`.
    *   - etc.
    *
    * For example:
    * {{{
    *   // 'x' is [1, 4]
    *   // 'y' is [2, 5]
    *   // 'z' is [3, 6]
    *   stack(Array(x, y, z)) == [[1, 4], [2, 5], [3, 6]]         // Packed along the first dimension.
    *   stack(Array(x, y, z), axis = 1) == [[1, 2, 3], [4, 5, 6]] // Packed along the second dimension.
    * }}}
    *
    * This op is the opposite of `unstack`.
    *
    * @param  inputs  Input tensors to be stacked.
    * @param  axis    Dimension along which to stack the input tensors.
    * @param  name    Name for the created op.
    * @param  context Op creation context.
    * @return Created op.
    * @throws IndexOutOfBoundsException If `axis` is not within the expected output tensor shape rank.
    */
  @throws[IndexOutOfBoundsException]
  def stack(inputs: Array[Op.Output], axis: Long = 0, name: String = "Stack")
      (implicit context: DynamicVariable[OpCreationContext]): Op.Output = {
    val inputsShape = inputs.head.shape
    inputs.tail.foreach(_.shape.assertIsCompatibleWith(inputsShape))
    if (inputsShape.rank != -1) {
      val expandedRank = inputsShape.rank + 1
      if (axis < -expandedRank || axis >= expandedRank)
        throw new IndexOutOfBoundsException(s"Provided axis, $axis, is not in [${-expandedRank}, $expandedRank).")
    }
    Op.Builder(context = context, opType = "Pack", name = name)
        .addInputList(inputs)
        .setAttribute("axis", axis)
        .build().outputs(0)
  }

  /** Creates an op that unpacks the provided dimension of a rank-`R` tensor into a list of rank-`(R-1)` tensors.
    *
    * The op unpacks `number` tensors from `input` by chipping it along the `axis` dimension. If `number == -1` (i.e.,
    * unspecified), its value is inferred from the shape of `input`. If `input.shape(axis)` is not known, then an
    * [[IllegalArgumentException]] is thrown.
    *
    * For example, given a tensor of shape `[A, B, C, D]`:
    *   - If `axis == 0`, then the `i`th tensor in the output is the slice `input(i, ::, ::, ::)` and each tensor in the
    * output will have shape `[B, C, D]`.
    *   - If `axis == 1`, then the `i`th tensor in the output is the slice `input(::, i, ::, ::)` and each tensor in the
    * output will have shape `[A, C, D]`.
    *   - If `axis == -1`, then the `i`th tensor in the output is the slice `input(::, ::, ::, i)` and each tensor in
    * the output will have shape `[A, B, C]`.
    *   - etc.
    *
    * This op is the opposite of `stack`.
    *
    * @param  input   Rank `R > 0` `Tensor` to be unstacked.
    * @param  number  Number of tensors to unstack. If set to `-1` (the default value), its value will be inferred.
    * @param  axis    Dimension along which to unstack the input tensor.
    * @param  name    Name for the created op.
    * @param  context Op creation context.
    * @return Created op.
    * @throws IndexOutOfBoundsException If `axis` is not within the range [-R, R).
    * @throws IllegalArgumentException  If `number` is not specified and its value cannot be inferred.
    */
  def unstack(input: Op.Output, number: Long = -1, axis: Long = 0, name: String = "Unstack")
      (implicit context: DynamicVariable[OpCreationContext]): Array[Op.Output] = {
    val num: Long = {
      if (number >= 0) {
        number
      } else {
        val inputShape = input.shape
        val inputShapeRank = inputShape.rank
        if (inputShapeRank != -1 && (axis < -inputShapeRank || axis >= inputShapeRank))
          throw new IndexOutOfBoundsException(
            s"Provided axis, $axis, is not in [${-inputShapeRank}, $inputShapeRank).")
        inputShape(axis.asInstanceOf[Int])
      }
    }
    if (num == -1)
      throw new IllegalArgumentException(s"Cannot infer number of tensors to unstack from shape '${input.shape}'.")
    Op.Builder(context = context, opType = "Unpack", name = name)
        .addInput(input)
        .setAttribute("num", num)
        .setAttribute("axis", axis)
        .build().outputs
  }
}
